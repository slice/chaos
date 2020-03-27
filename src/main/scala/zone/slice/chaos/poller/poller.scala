package zone.slice.chaos
package poller

import discord._
import publisher._
import source._
import source.discord._

import java.nio.file._
import java.util.concurrent.Executors

import cats.effect._
import cats.implicits._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext

class Poller[F[_]: Timer: ContextShift] private[chaos] (
    config: Config,
    blocker: Blocker,
)(
    implicit F: ConcurrentEffect[F],
    L: Logger[F],
) {
  protected val executionContext: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  /** Creates a [[Publisher]] from a [[PublisherSetting]].
    *
    * A [[PublisherSetting]] merely acts as a thin "configuration" object for
    * an actual [[Publisher]].
    */
  private[chaos] def buildPublisher(
      setting: PublisherSetting,
  )(implicit httpClient: Client[F]): Publisher[F] = setting match {
    case DiscordPublisherSetting(id, token, _) =>
      DiscordPublisher[F](Webhook(id, token), httpClient)
    case StdoutPublisherSetting(format, _) =>
      StdoutPublisher[F](format)
  }

  /** Publishes a [[Deploy]] to a set of [[Publisher]]s. */
  def publish(deploy: Deploy, publishers: Set[Publisher[F]]): F[Unit] = {
    publishers
      .map(publisher =>
        L.info(s"Publishing fresh ${deploy.build.branch} build to $publisher")
          *> publisher.publish(deploy),
      )
      .toVector
      .sequence
      .void
  }

  /** Processes an `Either[Throwable, Poll[Build]]` from the polling stream of
    * a [[Source]], publishing it to a set of [[Publisher]]s if necessary.
    */
  def pollTap(
      source: Source[F, Build],
      publishers: Set[Publisher[F]],
  )(either: Either[Throwable, Poll[Build]]): F[Unit] =
    either match {
      case Left(error) =>
        L.error(error)(s"Failed to scrape from $source")

      case Right(result) =>
        val build = result.build
        // Revert heuristics: simply check if the build number has gone down instead
        // of up.
        val isRevert = result.previous.exists(build.number < _.number)
        val deploy   = Deploy(result.build, isRevert)

        publish(deploy, publishers)
          .handleErrorWith(L.error(_)(show"Failed to publish $build"))
    }

  /** Resolves a list of [[PublisherSetting]]s into a mapping between
    * [[Source]]s and a set of [[Publisher]]s interested in those sources.
    *
    * Since each publisher setting specifies which sources it's interested in
    * through the source selector, we are able to derive a mapping between all
    * publishers and the sources that they will receive builds from. This means
    * that if more than one publisher is interested in a specific source, then
    * those source objects will be duplicated.
    */
  def resolve(settings: List[PublisherSetting])(
      implicit httpClient: Client[F],
  ): Map[SelectedSource[F, Build], Set[Publisher[F]]] = {

    /** Selects some sources from a selector string.
      *
      * The sources are returned inside of [[SelectedSource]] objects, so you
      * are able to refer back to the original selector string.
      */
    def selectSource(selector: String): Set[SelectedSource[F, Build]] = {
      selector match {
        case s"fe:$selector" =>
          val selected = Select[Branch].multiselect(selector)
          selected.map({
            case Selected(branch, normalizedSelector) =>
              SelectedSource(
                s"fe:$normalizedSelector",
                FrontendSource[F](branch, httpClient),
              )
          })
      }
    }

    // Since each publisher specifies its "desired sources", let's reverse the
    // mapping, creating a mapping from all desired sources to the publishers
    // that care about builds from those sources.
    //
    // This means that publisher objects will be duplicated (in the nested map
    // call), but it provides a more straightforward approach.
    settings
      .map({ setting =>
        // Our first step is to resolve the publisher setting objects and
        // selectors into publisher objects and source objects respectively.
        buildPublisher(setting) -> setting.scrape.flatMap(selectSource),
      })
      .flatMap({
        // If a publisher desires builds from more than one source, duplicate it
        // for each source it wants. This is so we can group them by source.
        case publisher -> sources => sources.map(source => publisher -> source)
      })
      .groupMap({
        // Now that we have a flat mapping of publisher to source, we can group
        // by source.
        case _ -> source => source
      })({
        // The entries are grouped by source already, so let's just keep the
        // publisher.
        case publisher -> _ => publisher
      })
      // Convert the list of publishers to a set.
      .view
      .mapValues(_.toSet)
      .toMap
  }

  private[chaos] def decodeStateStore(contents: String): Map[String, Int] =
    contents
      .linesIterator
      .collect {
        case s"$selector=$numberString" => (selector, numberString.toInt)
      }
      .toMap

  private[chaos] def encodeStateStore(store: Map[String, Int]): String =
    store
      .map {
        case selector -> number => s"$selector=$number"
      }
      .mkString("\n")

  private[chaos] def readStateFile: F[Option[Map[String, Int]]] = {
    import fs2.io.file
    import fs2.text

    val stateFilePath = Paths.get(config.stateFilePath)

    file.exists(blocker, stateFilePath).flatMap { exists =>
      if (exists)
        file
          .readAll[F](stateFilePath, blocker, 1024)
          .through(text.utf8Decode)
          .compile
          .string
          .map(decodeStateStore(_).some)
      else
        F.pure(None)
    }
  }

  /** Polls and publishes builds from all sources forever, persisting the last
    * known build in the state file.
    */
  def poller(
      implicit httpClient: Client[F],
  ): F[Unit] = {
    // Derive the source to publisher mapping.
    val mapping = resolve(config.publishers)

    import fs2.Pipe

    /** "Continuously overwrite" a string stream into a file, truncating the
      * file before each write.
      */
    def continuouslyOverwrite(path: Path): Pipe[F, String, Unit] = {
      import fs2.text.utf8Encode
      import fs2.io.file.WriteCursor

      val cursor =
        WriteCursor.fromPath(path, blocker, List(StandardOpenOption.CREATE))

      (in) =>
        in.flatMap(chunk =>
          Stream
            .resource(cursor)
            .flatMap(_.writeAll(Stream(chunk).through(utf8Encode)).void.stream),
        )
    }

    val defaultInitialState = Map[String, Int]()
    val stateFilePath       = Paths.get(config.stateFilePath)

    for {
      initialState <- readStateFile.map(
        _.getOrElse(defaultInitialState),
      )
      poll = Stream
        .emits(mapping.toList)
        .map({
          case (selectedSource @ SelectedSource(_, source), publishers) =>
            val initialBuild = initialState
              .get(selectedSource.selector)
              .map { number =>
                // The branch is irrelevant because only the number is
                // compared.
                Build(Branch.Stable, "", number, AssetBundle.empty)
              }

            source
              .poll(config.interval, initialBuild)
              // Tap into the poll stream to publish builds.
              .evalTap(pollTap(source, publishers) _)
              // Add the selected source to any emitted builds, so we have the
              // context when saving.
              .map((selectedSource, _))
        })
        // Run all polling streams concurrently.
        .parJoinUnbounded
        // Maintain a map of the latest builds for each source.
        .scan(Map[String, Int]()) {
          case (accumulator, (selectedSource, Right(Poll(build, _)))) =>
            accumulator + (selectedSource.selector -> build.number)
        }
        // Encode the map into a simple key-value store.
        .map(encodeStateStore)
        // Every time a new build is published, save the store into a file.
        .through(continuouslyOverwrite(stateFilePath))
        .compile
        .drain
      _ <- L.info(s"Scraping ${mapping.size} source(s): $mapping")
      _ <- poll
    } yield ()
  }

  /** Starts this poller and runs it forever. */
  def runForever: F[Unit] =
    L.info(show"Starting poller (interval: ${config.interval})") *>
      BlazeClientBuilder[F](executionContext).resource.use {
        implicit httpClient => poller
      }
}

object Poller {

  /** Creates a new poller and runs it forever. */
  def apply[F[_]: ConcurrentEffect: Timer: Logger: ContextShift](
      config: Config,
  ): F[Unit] =
    Blocker[F].use { blocker => new Poller(config, blocker).runForever }
}

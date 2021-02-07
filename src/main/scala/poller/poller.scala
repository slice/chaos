package zone.slice.chaos
package poller

import stream.continuouslyOverwrite
import discord._
import publisher._
import source._
import source.discord._

import java.nio.file._
import java.util.concurrent.Executors

import cats.Order
import cats.data.NonEmptyList
import cats.effect._
import cats.implicits._
import fs2.Stream
import upperbound.Limiter
import upperbound.syntax.rate._
import io.chrisdavenport.log4cats.Logger
import org.http4s.client.Client
import org.http4s.client.middleware.FollowRedirect
import org.http4s.client.blaze.BlazeClientBuilder

import scala.collection.immutable.SortedMap
import scala.concurrent.ExecutionContext

class Poller[F[_]: Timer: ContextShift] private[chaos] (config: Config)(
    httpClient: Client[F],
    limiter: Limiter[F],
    blocker: Blocker,
)(implicit
    F: ConcurrentEffect[F],
    L: Logger[F],
) {

  /**
    * Creates a [[Publisher]] from a [[PublisherSetting]].
    *
    * A [[PublisherSetting]] merely acts as a thin "configuration" object for
    * an actual [[Publisher]].
    */
  private[chaos] def buildPublisher(
      setting: PublisherSetting,
  ): Publisher[F] =
    setting match {
      case DiscordPublisherSetting(id, token, _) =>
        DiscordPublisher[F](Webhook(id, token), httpClient)
      case StdoutPublisherSetting(format, _) =>
        StdoutPublisher[F](format)
      case WebhookPublisherSetting(uri, _) =>
        WebhookPublisher[F](uri, httpClient)
    }

  /**
    * Ratelimits a [[Publisher]] so it can't publish too quickly.
    */
  private[chaos] def limitPublisher(
      publisher: Publisher[F],
  ): Publisher[F] = {
    new Publisher[F] {
      override def publish(deploy: Deploy): F[Unit] = {
        val submit = limiter.submit(publisher.publish(deploy), 1)
        L.info(show"Enqueueing publish for $deploy") >> submit
      }

      override def toString: String = s"Limited($publisher)"
    }
  }

  /** Resolve a source selector string into a set of selected [[Source]]s. */
  def selectSource(
      selector: String,
  ): Set[SelectedSource[F, Build]] = {
    selector match {
      case s"fe:$selector" =>
        val branch = Select[Branch].multiselect(selector)
        branch.map({
          case Selected(branch, normalizedSelector) =>
            SelectedSource(
              s"fe:$normalizedSelector",
              FrontendSource[F](branch, httpClient),
            )
        })
      case s"courgette:$platformS-$branchS-$archS" =>
        val platforms = Select[Platform].multiselect(platformS)
        val branches  = Select[Branch].multiselect(branchS)
        val arches    = Select[Arch].multiselect(archS)

        for (
          Selected(plat, platSelector)     <- platforms;
          Selected(branch, branchSelector) <- branches;
          Selected(arch, archSelector)     <- arches
        ) yield {
          SelectedSource(
            s"courgette:$platSelector-$branchSelector-$archSelector",
            CourgetteSource[F](branch, plat, arch, httpClient),
          )
        }
      case s"host:$platformSelector-$branchSelector" =>
        val platforms = Select[Platform].multiselect(platformSelector)
        val branches  = Select[Branch].multiselect(branchSelector)

        for (
          Selected(platform, platformS) <- platforms;
          Selected(branch, branchS)     <- branches
        ) yield {
          SelectedSource(
            s"host:$platformS-$branchS",
            HostSource[F](branch, platform, httpClient),
          )
        }
      case _ => Set.empty
    }
  }

  def stateFilePath: Path = Paths.get(config.stateFilePath)

  /** Create a fake build object.
    *
    * This is needed in order to load in the versions stored in the state
    * file, so that pollers don't think that there's always a new build.
    */
  def fakeBuild(version: String): Build =
    HostBuild(Branch.Stable, Platform.Windows, version, "", none)

  /** Polls and publishes builds from all sources forever, persisting the last
    * known build in the state file.
    */
  def run: F[Unit] = {
    // Construct a sequence of each publisher and the sources it needs.
    // Publishers which publish from multiple sources are duplicated for each
    // source.
    val duplicatedMapping: List[(Publisher[F], SelectedSource[F, Build])] =
      for {
        setting        <- config.publishers
        selector       <- setting.scrape
        selectedSource <- selectSource(selector)
      } yield (
        (buildPublisher _ andThen limitPublisher _)(setting),
        selectedSource,
      )

    // It'd normally be logically impossible to order sources, but luckily we
    // have the selector here.
    implicit val selectedSourceOrder: Order[SelectedSource[F, Build]] =
      Order.by(_.selector)

    // Go from a duplicated publisher to source mapping to a source to
    // publishers mapping.
    val groupedMapping
        : SortedMap[SelectedSource[F, Build], NonEmptyList[Publisher[F]]] =
      duplicatedMapping.groupByNel(_._2).fmap(_.fmap(_._1))

    for {
      _ <- groupedMapping.toVector.traverse {
        case (selectedSource, publishers) =>
          val plural = if (publishers.size == 1) "" else "s"
          val heading = s"""[Mapping] Source "${selectedSource.selector}"
                           | is being consumed by ${publishers.size}
                           | publisher$plural:""".stripMargin
            .replaceAll("\n", "")
          L.info(heading) >> publishers.traverse { publisher =>
            L.info(s"[Mapping]     $publisher")
          }
      }

      initialState <-
        State
          .read(stateFilePath, blocker)
          .map(_.getOrElse(Map[String, String]()))

      go =
        Stream
          .emits(groupedMapping.toVector)
          .map {
            case (
                  selectedSource @ SelectedSource(selector, source),
                  publishers,
                ) =>
              val initialBuild = initialState.get(selector).map(fakeBuild(_))
              source
                .limited(limiter)(0)
                .poll(config.interval, initialBuild)
                .evalTap {
                  case Left(error) =>
                    L.error(error)(s"Failed to scrape from $selectedSource")
                  case Right(Poll(build, prev)) =>
                    val isRevert = prev.exists(build.number < _.number)
                    val deploy   = Deploy(build, isRevert)
                    publishers.traverse(_.publish(deploy))
                }
                // Annotate poll objects with the source they came from so we
                // can update the state file.
                .map((selectedSource, _))
          }
          .parJoinUnbounded
          .scan(initialState) {
            case (acc, selectedSource -> Right(Poll(build, _))) =>
              acc + (selectedSource.selector -> build.version)
            case (acc, _) => acc
          }
          .map(State.encode)
          .through(continuouslyOverwrite(blocker, stateFilePath))

      _ <- go.compile.drain
    } yield ()
  }
}

object Poller {

  /** Creates a new poller and runs it forever. */
  def apply[F[_]: ConcurrentEffect: Timer: Logger: ContextShift](
      config: Config,
  ): F[Unit] = {
    val executionContext: ExecutionContext =
      ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

    val resource = for {
      blocker    <- Blocker[F]
      httpClient <- BlazeClientBuilder[F](executionContext).resource
      limiter    <- Limiter.start[F](1 every config.requestRatelimit)
    } yield new Poller[F](config)(
      FollowRedirect(5)(httpClient),
      limiter,
      blocker,
    )

    resource.use(_.run)
  }
}

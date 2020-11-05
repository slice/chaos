package zone.slice.chaos
package poller

import stream.continuouslyOverwrite
import discord._
import publisher._
import source._
import source.discord._

import java.nio.file._
import java.util.concurrent.Executors

import cats.effect._
import cats.implicits._
import fs2.Stream
import upperbound.Limiter
import upperbound.syntax.rate._
import io.chrisdavenport.log4cats.Logger
import org.http4s.client.Client
import org.http4s.client.middleware.FollowRedirect
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext

class Poller[F[+_]: Timer: ContextShift] private[chaos] (
    config: Config,
    blocker: Blocker,
)(implicit
    F: ConcurrentEffect[F],
    L: Logger[F],
) {
  protected val executionContext: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  /**
    * Creates a [[Publisher]] from a [[PublisherSetting]].
    *
    * A [[PublisherSetting]] merely acts as a thin "configuration" object for
    * an actual [[Publisher]].
    */
  private[chaos] def buildPublisher(
      setting: PublisherSetting,
  )(implicit httpClient: Client[F]): Publisher[F] =
    setting match {
      case DiscordPublisherSetting(id, token, _) =>
        DiscordPublisher[F](Webhook(id, token), httpClient)
      case StdoutPublisherSetting(format, _) =>
        StdoutPublisher[F](format)
      case WebhookPublisherSetting(uri, _) =>
        WebhookPublisher[F](uri, httpClient)
    }

  /**
    * Wraps a [[Publisher]] so that it becomes a no-op for builds outside of a
    * certain source and becomes ratelimited.
    */
  private[chaos] def wrapPublisher(
      publisher: Publisher[F],
      source: Source[F, Build],
  )(implicit limiter: Limiter[F]): Publisher[F] = {
    new Publisher[F] {
      override def publish(deploy: Deploy): F[Unit] = {
        val applicable = deploy.build match {
          case build: HostBuild
              if (build.branch, build.branch) == source.variant =>
            true
          case build: FrontendBuild if build.branch == source.variant => true
          case _                                                      => false
        }
        val submit = limiter.submit(publisher.publish(deploy), 1)
        (L.info(show"Enqueueing publish for $deploy") >> submit)
          .whenA(applicable)
      }
    }
  }

  /** Resolve a source selector string into a set of selected [[Source]]s. */
  def selectSource(
      selector: String,
  )(implicit httpClient: Client[F]): Set[SelectedSource[F, Build]] = {
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
  def poller(implicit
      httpClient: Client[F],
      LM: Limiter[F],
  ): F[Unit] = {
    val mapped: Set[(Publisher[F], SelectedSource[F, Build])] = (for {
      setting        <- config.publishers
      selector       <- setting.scrape
      selectedSource <- selectSource(selector)
    } yield (
      wrapPublisher(buildPublisher(setting), selectedSource.source),
      selectedSource,
    )).toSet

    val publishers = mapped.map(_._1)
    val sources    = mapped.map(_._2)

    for {
      initialState <-
        State
          .read(stateFilePath, blocker)
          .map(_.getOrElse(Map[String, String]()))

      go =
        Stream
          .emits(sources.toVector)
          .map {
            case selectedSource @ SelectedSource(selector, source) =>
              val initialBuild = initialState.get(selector).map(fakeBuild(_))
              Source
                .limited(source)
                .poll(config.interval, initialBuild)
                .map((selectedSource, _))
          }
          .parJoinUnbounded
          .evalTap { item =>
            item match {
              case source -> Left(error) =>
                L.error(error)(s"Failed to scrape from $source")
              case _ -> Right(Poll(build, prev)) =>
                val isRevert = prev.exists(build.number < _.number)
                val deploy   = Deploy(build, isRevert)
                publishers.toVector.traverse(_.publish(deploy))
            }
          }
          .scan(initialState) {
            case (acc, selectedSource -> Right(Poll(build, _))) =>
              acc + (selectedSource.selector -> build.version)
            case (acc, _) => acc
          }
          .map(State.encode)
          .through(continuouslyOverwrite(blocker, stateFilePath))

      _ <- L.info(s"Scraping ${sources.size} source(s): $mapped")
      _ <- go.compile.drain
    } yield ()
  }

  /** Starts this poller and runs it forever. */
  def run: F[Unit] = {
    val resources = for {
      httpClient <- BlazeClientBuilder[F](executionContext).resource
      limiter    <- Limiter.start[F](1 every config.requestRatelimit)
    } yield {
      (FollowRedirect(5)(httpClient), limiter)
    }

    resources.use {
      case (implicit0(httpClient: Client[F]), implicit0(limiter: Limiter[F])) =>
        poller
    }
  }
}

object Poller {

  /** Creates a new poller and runs it forever. */
  def apply[F[+_]: ConcurrentEffect: Timer: Logger: ContextShift](
      config: Config,
  ): F[Unit] =
    Blocker[F].use { blocker => new Poller[F](config, blocker).run }
}

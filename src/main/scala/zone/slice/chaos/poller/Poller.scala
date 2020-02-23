package zone.slice.chaos
package poller

import discord._
import publisher._
import scraper._
import source._

import java.util.concurrent.Executors

import cats.effect._
import cats.implicits._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class Poller[F[_]: Timer] private[chaos] (config: Config)(
    implicit F: ConcurrentEffect[F],
    L: Logger[F],
) {
  protected val executionContext: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  /** Equivalent to `Stream.awakeDelay`, but doesn't do a first sleep. */
  protected def eagerAwakeDelay(
      rate: FiniteDuration,
  ): Stream[F, FiniteDuration] =
    Stream(0.seconds) ++ Stream.awakeDelay[F](rate)

  /** Builds a [[publisher.Publisher]] from a [[PublisherSetting]]. */
  private[chaos] def buildPublisher(
      setting: PublisherSetting,
  )(implicit httpClient: Client[F]): Publisher[F] = setting match {
    case DiscordPublisherSetting(id, token, _) =>
      new DiscordPublisher[F](Webhook(id, token), httpClient)
    case StdoutPublisherSetting(format, _) =>
      new StdoutPublisher[F](format)
  }

  /** Publishes a [[discord.Deploy]] to a list of [[PublisherSetting]]s. */
  def publish(deploy: Deploy, publisherSettings: List[PublisherSetting])(
      implicit httpClient: Client[F],
  ): F[Unit] = {
    publisherSettings
      .filter(_.branches.contains(deploy.build.branch))
      .map(buildPublisher)
      .map(publisher =>
        L.info(s"Publishing fresh ${deploy.build.branch} build to $publisher")
          *> publisher.publish(deploy),
      )
      .sequence
      .void
  }

  /**
    * Processes a [[discord.Build]] from a poll, creating a [[discord.Deploy]]
    * from it and publishing it to the configured publishers.
    */
  def pollTap(
      result: PollResult[Build],
  )(implicit httpClient: Client[F]): F[Unit] = {
    val build = result.build
    // Revert heuristics: simply check if the build number has gone down instead
    // of up.
    val isRevert = result.previous.exists(build.buildNumber < _.buildNumber)
    val deploy   = Deploy(result.build, isRevert)

    publish(deploy, config.publishers)
      .handleErrorWith(L.error(_)(show"Failed to publish $build"))
  }

  def frontendPoller(
      branches: Set[Branch],
      source: DiscordFrontendSource[F],
  )(implicit httpClient: Client[F]): F[Unit] = {
    Stream
      .emits(branches.toSeq)
      .map(branch =>
        source
          .poll(branch, rate = config.interval)(
            pollTap,
            L.error(_)(show"Failed to scrape $branch"),
          )
          .drain,
      )
      .parJoinUnbounded
      .compile
      .drain
  }

  /** Polls and publishes builds from all branches forever. */
  def poller(implicit httpClient: Client[F]): F[Unit] = {
    // Compute the branches that we have to scrape from. If all publishers are
    // configured to only scrape from the Canary branch, then we can simply only
    // scrape from that branch.
    val specifiedBranches = config.publishers.flatMap(_.branches).toSet
    val branches =
      if (specifiedBranches.isEmpty) Branch.all else specifiedBranches

    val scraper               = new Scraper(httpClient)
    val discordFrontendSource = new DiscordFrontendSource[F](scraper)

    L.info(show"Scraping ${branches.size} branch(es): ${branches}") *>
      frontendPoller(branches, discordFrontendSource)
  }

  /** Starts this poller and runs it forever. */
  def runForever: F[Unit] =
    L.info(show"Starting poller (interval: ${config.interval})") *>
      BlazeClientBuilder[F](executionContext).resource.use {
        implicit httpClient =>
          poller
      }
}

object Poller {

  /** Creates a new poller and runs it forever. */
  def apply[F[_]: ConcurrentEffect: Timer: Logger](config: Config): F[Unit] =
    new Poller(config).runForever
}

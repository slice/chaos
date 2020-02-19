package zone.slice.chaos
package poller

import discord._
import publisher._
import scraper._

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

  /** A metered fs2 Stream of all builds from some branches. */
  def scrapeStream(branches: Set[Branch], rate: FiniteDuration)(
      implicit httpClient: Client[F],
  ): Stream[F, (Branch, Either[Throwable, Build])] =
    Stream
      .emits(branches.toList)
      .map { branch =>
        eagerAwakeDelay(rate)
          .as(branch)
          .zipRight(branch.buildStream(new Scraper(httpClient)).attempt.repeat)
          .map((branch, _))
      }
      .parJoin(Branch.all.size)

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

  /** Consumes a single build, returning an updated [[BuildMap]]. */
  def consumeBuild(
      freshnessMap: BuildMap.Type,
      build: Build,
  )(implicit httpClient: Client[F]): F[BuildMap.Type] = {
    val currentBuildNumber = build.buildNumber
    val lastBuildNumber    = freshnessMap.getOrElse(build.branch, none[Int])

    if (lastBuildNumber.forall(currentBuildNumber != _)) {
      // Compare the build number of the build we just received to the last build
      // number.
      val isRevert = lastBuildNumber.exists(currentBuildNumber < _)
      val deploy   = Deploy(build, isRevert)

      publish(deploy, config.publishers)
        .handleErrorWith(L.error(_)(show"Failed to publish $build"))
        .as(freshnessMap.updated(build.branch, build.buildNumber.some))
    } else
      F.pure(freshnessMap)
  }

  /** Polls and publishes builds from all branches forever. */
  def poller(implicit httpClient: Client[F]): Stream[F, Unit] = {
    // Compute the branches that we have to scrape from. If all publishers are
    // configured to only scrape from the Canary branch, then we can simply only
    // scrape from that branch.
    val specifiedBranches = config.publishers.flatMap(_.branches).toSet
    val branches =
      if (specifiedBranches.isEmpty) Branch.all else specifiedBranches

    Stream.eval(L.info(show"Scraping ${branches.size} branch(es): ${branches}")) ++
      scrapeStream(branches, rate = config.interval)
        .evalScan(BuildMap.default) {
          case (freshnessMap, (branch, Left(error))) =>
            L.error(error)(show"Failed to scrape $branch")
              .as(freshnessMap)
          case (freshnessMap, (_, Right(build))) =>
            consumeBuild(freshnessMap, build)
        }
        .drain
  }

  /** Starts this poller and runs it forever. */
  def runForever: F[Unit] =
    L.info(show"Starting poller (interval: ${config.interval})") *>
      Stream
        .resource(BlazeClientBuilder[F](executionContext).resource)
        .flatMap { implicit httpClient =>
          poller
        }
        .compile
        .drain
}

object Poller {

  /** Creates a new poller and runs it forever. */
  def apply[F[_]: ConcurrentEffect: Timer: Logger](config: Config): F[Unit] =
    new Poller(config).runForever
}

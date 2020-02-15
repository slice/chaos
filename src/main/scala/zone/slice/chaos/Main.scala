package zone.slice.chaos

import discord._
import publisher._
import scraper._

import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.config.parser
import io.circe.generic.auto._
import io.circe.config.syntax._
import fs2._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Main extends IOApp {
  // ~_~
  protected implicit def unsafeLogger[F[_]: Sync]: Logger[F] =
    Slf4jLogger.getLogger[F]

  /** Similar to `Stream.awakeEvery`, but doesn't do a first sleep. */
  def eagerAwakeEvery[F[_]: ConcurrentEffect: Timer](
    rate: FiniteDuration
  ): Stream[F, FiniteDuration] =
    Stream(0.seconds) ++ Stream.awakeEvery[F](rate)

  /** A metered fs2 Stream of all builds from all branches. */
  def allBuilds[F[_]: ConcurrentEffect: Timer](rate: FiniteDuration)(
    implicit httpClient: Client[F]
  ): Stream[F, (Branch, Either[Throwable, Build])] =
    Stream
      .emits(Branch.all)
      .map { branch =>
        eagerAwakeEvery(rate)
          .as(branch)
          .zipRight(branch.buildStream(new Scraper(httpClient)).attempt.repeat)
          .map((branch, _))
      }
      .parJoin(Branch.all.size)

  /** Builds a list of [[Publisher]]s from a list of [[PublisherSetting]]s. */
  def buildPublishers[F[_]: Sync](
    configured: List[PublisherSetting]
  )(implicit httpClient: Client[F]): List[Publisher[F]] =
    configured
      .map {
        case DiscordPublisherSetting(id, token) =>
          new DiscordPublisher[F](Webhook(id, token), httpClient)
        case StdoutPublisherSetting(format) =>
          new StdoutPublisher[F](format)
      }

  /** Publishes a [[Build]] to a list of [[Publisher]]s. */
  def publish[F[_]: ConcurrentEffect](
    build: Build,
    publishers: List[Publisher[F]]
  ): F[Unit] = {
    val message =
      show"Fresh build for ${build.branch} (${build.buildNumber}), publishing"
    for {
      _ <- Logger[F].info(message)
      _ <- publishers.map(_.publish(build)).sequence
    } yield ()
  }

  /** Consumes a single build, returning an updated [[BuildMap]]. */
  def consumeBuild[F[_]: ConcurrentEffect: Client](
    freshnessMap: BuildMap.Type,
    build: Build,
    config: Config
  ): F[BuildMap.Type] =
    if (freshnessMap
          .getOrElse(build.branch, none[Int])
          .forall(build.buildNumber > _))
      publish[F](build, publishers = buildPublishers(config.publishers))
        .handleErrorWith(Logger[F].error(_)(show"Failed to publish $build"))
        .as(freshnessMap.updated(build.branch, build.buildNumber.some))
    else
      Sync[F].pure(freshnessMap)

  /** Polls and publishes builds from all branches forever. */
  def poller[F[_]: ConcurrentEffect: Timer: Client](
    config: Config
  ): Stream[F, Unit] =
    allBuilds(rate = config.interval)
      .evalScan(BuildMap.default) {
        case (freshnessMap, (branch, Left(error))) =>
          Logger[F]
            .error(error)(show"Failed to scrape $branch")
            .as(freshnessMap)
        case (freshnessMap, (_, Right(build))) =>
          consumeBuild(freshnessMap, build, config = config)
      }
      .drain

  /** Starts the poller from a [[Config]]. */
  def startPoller[F[_]: ConcurrentEffect: Timer](config: Config): F[ExitCode] =
    Logger[F].info(show"Starting poller (interval: ${config.interval})") *>
      Stream
        .resource(BlazeClientBuilder[F](ExecutionContext.global).resource)
        .flatMap { implicit httpClient =>
          poller(config)
        }
        .compile
        .drain
        .as(ExitCode.Success)

  def program[F[_]: ConcurrentEffect: Timer]: F[ExitCode] =
    parser
      .decodeF[F, Config]()
      .attemptT
      .foldF(
        error =>
          Sync[F]
            .delay(Console.err.println(s"Failed to load config file: $error"))
            .as(ExitCode.Error),
        startPoller(_)
      )

  override def run(args: List[String]): IO[ExitCode] =
    program[IO]
}

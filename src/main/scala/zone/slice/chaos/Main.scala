package zone.slice.chaos

import java.util.concurrent.Executors

import discord._
import publisher._
import scraper._

import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.DecodingFailure
import io.circe.config.parser
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
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

  protected val executionContext: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  /** Similar to `Stream.awakeEvery`, but doesn't do a first sleep. */
  def eagerAwakeEvery[F[_]: ConcurrentEffect: Timer](
    rate: FiniteDuration
  ): Stream[F, FiniteDuration] =
    Stream(0.seconds) ++ Stream.awakeEvery[F](rate)

  /** A metered fs2 Stream of all builds from some branches. */
  def scrapeStream[F[_]: ConcurrentEffect: Timer](branches: Set[Branch],
                                                  rate: FiniteDuration)(
    implicit httpClient: Client[F]
  ): Stream[F, (Branch, Either[Throwable, Build])] =
    Stream
      .emits(branches.toList)
      .map { branch =>
        eagerAwakeEvery(rate)
          .as(branch)
          .zipRight(branch.buildStream(new Scraper(httpClient)).attempt.repeat)
          .map((branch, _))
      }
      .parJoin(Branch.all.size)

  /** Builds a [[Publisher]] from a [[PublisherSetting]]. */
  def buildPublisher[F[_]: Sync](
    setting: PublisherSetting
  )(implicit httpClient: Client[F]): Publisher[F] = setting match {
    case DiscordPublisherSetting(id, token, _) =>
      new DiscordPublisher[F](Webhook(id, token), httpClient)
    case StdoutPublisherSetting(format, _) =>
      new StdoutPublisher[F](format)
  }

  /** Publishes a [[Build]] to a list of [[PublisherSetting]]s. */
  def publish[F[_]: ConcurrentEffect: Client](
    build: Build,
    publisherSettings: List[PublisherSetting]
  ): F[Unit] = {
    publisherSettings
      .filter(_.branches.contains(build.branch))
      .map(buildPublisher[F])
      .map(
        publisher =>
          Logger[F]
            .info(s"Publishing fresh ${build.branch} build to $publisher")
            *> publisher.publish(build)
      )
      .sequence
      .void
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
      publish[F](build, config.publishers)
        .handleErrorWith(Logger[F].error(_)(show"Failed to publish $build"))
        .as(freshnessMap.updated(build.branch, build.buildNumber.some))
    else
      Sync[F].pure(freshnessMap)

  /** Polls and publishes builds from all branches forever. */
  def poller[F[_]: ConcurrentEffect: Timer: Client](
    config: Config
  ): Stream[F, Unit] = {
    // Compute the branches that we have to scrape from. If all publishers are
    // configured to only scrape from the Canary branch, then we can simply only
    // scrape from that branch.
    val specifiedBranches = config.publishers.flatMap(_.branches).toSet
    val branches =
      if (specifiedBranches.isEmpty) Branch.all else specifiedBranches

    Stream.eval(
      Logger[F].info(show"Scraping ${branches.size} branch(es): ${branches}")
    ) ++
      scrapeStream(branches, rate = config.interval)
        .evalScan(BuildMap.default) {
          case (freshnessMap, (branch, Left(error))) =>
            Logger[F]
              .error(error)(show"Failed to scrape $branch")
              .as(freshnessMap)
          case (freshnessMap, (_, Right(build))) =>
            consumeBuild(freshnessMap, build, config = config)
        }
        .drain
  }

  /** Starts the poller from a [[Config]]. */
  def startPoller[F[_]: ConcurrentEffect: Timer](config: Config): F[Unit] =
    Logger[F].info(show"Starting poller (interval: ${config.interval})") *>
      Stream
        .resource(BlazeClientBuilder[F](executionContext).resource)
        .flatMap { implicit httpClient =>
          poller(config)
        }
        .compile
        .drain

  def eput[F[_]: Sync](message: String): F[Unit] =
    Sync[F].delay(Console.err.println(message))

  implicit val circeConfiguration: Configuration =
    Configuration.default.withDefaults

  def program[F[_]: ConcurrentEffect: Timer]: F[ExitCode] =
    parser
      .decodeF[F, Config]()
      .attemptT
      .foldF(
        {
          case decodingFailure: DecodingFailure =>
            eput(show"Failed to decode config file: $decodingFailure")
              .as(ExitCode.Error)
          case error =>
            eput(s"Failed to load config file: $error")
              .as(ExitCode.Error)
        },
        startPoller(_).as(ExitCode.Success)
      )

  override def run(args: List[String]): IO[ExitCode] =
    program[IO]
}

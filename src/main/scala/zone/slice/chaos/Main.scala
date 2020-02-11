package zone.slice.chaos

import discord._
import publisher._
import scraper._
import scraper.errors._

import cats.data.EitherT
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
  type BuildMap = Map[Branch, Option[Int]]

  // ~_~
  protected implicit def unsafeLogger[F[_]: Sync]: Logger[F] =
    Slf4jLogger.getLogger[F]

  /** Similar to `Stream.awakeEvery`, but doesn't do a first sleep. */
  def eagerAwakeEvery[F[_]: ConcurrentEffect: Timer](
    rate: FiniteDuration
  ): Stream[F, FiniteDuration] =
    Stream(0.seconds) ++ Stream.awakeEvery[F](rate)

  def allBuildsStream[F[_]: ConcurrentEffect: Timer](
    httpClient: Client[F],
    rate: FiniteDuration
  ): Stream[F, Stream[F, Either[ScraperError, Build]]] =
    Stream.emits(Branch.all).map { branch =>
      eagerAwakeEvery(rate)
        .zipRight(branch.buildStream(new Scraper(httpClient)))
        .evalTap {
          case Left(error) =>
            Logger[F].error(error)(show"Failed to scrape $branch")
          case Right(build) =>
            Logger[F].debug(show"Scraped $branch: $build")
        }
    }

  /** Builds a list of [[Publisher]]s from a list of [[PublisherSetting]]s. */
  def buildPublishers[F[_]: Sync](
    configured: List[PublisherSetting]
  )(implicit httpClient: Client[F]): List[Publisher[F, Exception]] =
    configured
      .map {
        case DiscordPublisherSetting(id, token) =>
          new DiscordPublisher[F](Webhook(id, token), httpClient)
        case StdoutPublisherSetting(format) =>
          new StdoutPublisher[F](format)
      }
      .map(_.asInstanceOf[Publisher[F, Exception]]) // :thinking:

  /** Publishes a [[Build]] to a list of [[Publisher]]s. */
  def publish[F[_]: ConcurrentEffect](
    build: Build,
    publishers: List[Publisher[F, Exception]]
  ): EitherT[F, Exception, Unit] = {
    val message =
      show"Fresh build for ${build.branch} (${build.buildNumber}), publishing"
    for {
      _ <- EitherT.right(Logger[F].info(message))
      _ <- publishers.map(_.publish(build)).sequence
    } yield ()
  }

  /** Processes a single build, updating a [[BuildMap]] with the latest build. */
  def processBuild[F[_]: ConcurrentEffect: Client](
    freshnessMap: BuildMap,
    build: Build,
    config: Config
  ): F[BuildMap] =
    if (freshnessMap
          .getOrElse(build.branch, none[Int])
          .forall(build.buildNumber > _))
      publish[F](build, publishers = buildPublishers(config.publishers))
        .valueOrF(Logger[F].error(_)(show"Failed to publish $build"))
        .as(freshnessMap.updated(build.branch, build.buildNumber.some))
    else
      Sync[F].pure(freshnessMap)

  def poller[F[_]: ConcurrentEffect: Timer](config: Config): F[Unit] =
    Stream
      .resource(BlazeClientBuilder[F](ExecutionContext.global).resource)
      .flatMap { implicit httpClient =>
        allBuildsStream(httpClient, rate = config.interval)
          .parJoin(Branch.all.size)
          .evalScan(Branch.all.map((_, none[Int])).toMap) {
            (freshnessMap, result) =>
              result match {
                case Right(build) =>
                  processBuild(freshnessMap, build, config = config)
                case _ => Sync[F].pure(freshnessMap)
              }
          }
      }
      .compile
      .drain

  def startPoller[F[_]: ConcurrentEffect: Timer](config: Config): F[ExitCode] =
    for {
      _ <- Logger[F].info(show"Starting poller (interval: ${config.interval})")
      _ <- poller[F](config)
    } yield ExitCode.Success

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

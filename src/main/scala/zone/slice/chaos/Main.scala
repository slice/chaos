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

  def publish[F[_]: ConcurrentEffect](build: Build)(
    implicit httpClient: Client[F],
    config: Config
  ): EitherT[F, Exception, Unit] = {
    val publishers = config.publishers
      .map {
        case DiscordPublisherSetting(id, token) =>
          new DiscordPublisher[F](Webhook(id, token), httpClient)
      }
      .map(_.asInstanceOf[Publisher[F, Exception]]) // uhh

    val message =
      show"Fresh build for ${build.branch} (${build.buildNumber}), publishing"
    for {
      _ <- EitherT.right(Logger[F].info(message))
      _ <- publishers.map(_.publish(build)).sequence
    } yield ()
  }

  def scanBuild[F[_]: ConcurrentEffect: Client](
    freshnessMap: BuildMap,
    build: Build
  )(implicit config: Config): F[BuildMap] =
    if (freshnessMap
          .getOrElse(build.branch, none[Int])
          .forall(build.buildNumber > _))
      publish[F](build).value
        .flatTap {
          case Left(error) =>
            Logger[F].error(error)(show"Failed to publish $build")
          case _ => Sync[F].pure(())
        }
        .as(freshnessMap.updated(build.branch, build.buildNumber.some))
    else
      Sync[F].pure(freshnessMap)

  def scrapeAndPublishLoop[F[_]: ConcurrentEffect: Timer](
    implicit config: Config
  ): F[Unit] =
    Stream
      .resource(BlazeClientBuilder[F](ExecutionContext.global).resource)
      .flatMap { implicit httpClient =>
        allBuildsStream(httpClient, rate = config.interval)
          .parJoin(Branch.all.size)
          .evalScan(Branch.all.map((_, none[Int])).toMap) {
            (freshnessMap, result) =>
              result match {
                case Right(build) => scanBuild(freshnessMap, build)
                case _            => Sync[F].pure(freshnessMap)
              }
          }
      }
      .compile
      .drain

  def program[F[_]: ConcurrentEffect: Timer]: F[ExitCode] =
    parser
      .decodeF[F, Config]()
      .attemptT
      .foldF(
        error =>
          Sync[F]
            .delay(Console.err.println(s"Failed to load `chaos.conf`: $error"))
            .as(ExitCode.Error),
        implicit config =>
          Logger[F].info("Starting scrape and publish loop") *>
            scrapeAndPublishLoop[F].as(ExitCode.Success)
      )

  override def run(args: List[String]): IO[ExitCode] =
    program[IO]
}

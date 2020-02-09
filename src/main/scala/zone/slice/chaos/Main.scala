package zone.slice.chaos

import discord._
import scraper._
import scraper.errors._

import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import cats.implicits._
import cats.effect._
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
            Logger[F].error(error)(show"failed to scrape $branch")
          case Right(build) =>
            Logger[F].info(show"scraped $branch: $build")
        }
    }

  def notify[F[_]: ConcurrentEffect](build: Build): F[Unit] = {
    Logger[F].info(show"notifying for $build")
  }

  def scanBuild[F[_]: ConcurrentEffect](freshnessMap: BuildMap,
                                        build: Build): F[BuildMap] =
    if (freshnessMap
          .getOrElse(build.branch, none[Int])
          .forall(build.buildNumber > _))
      notify[F](build)
        .as(freshnessMap.updated(build.branch, build.buildNumber.some))
    else
      Sync[F].pure(freshnessMap)

  def program[F[_]: ConcurrentEffect: Timer]: F[Unit] =
    Stream
      .resource(BlazeClientBuilder[F](ExecutionContext.global).resource)
      .flatMap { scraper =>
        allBuildsStream(scraper, rate = 5.seconds)
          .parJoin(Branch.all.size)
      }
      .evalScan(Branch.all.map((_, none[Int])).toMap) {
        (freshnessMap, result) =>
          result match {
            case Right(build) => scanBuild(freshnessMap, build)
            case _            => Sync[F].pure(freshnessMap)
          }
      }
      .compile
      .drain

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- Logger[IO].info("starting scrape loop")
      _ <- program[IO]
    } yield ExitCode.Success
  }
}

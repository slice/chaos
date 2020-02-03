package zone.slice.chaos

import discord._
import scraper._
import scraper.errors._

import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import cats.implicits._
import cats.effect._
import fs2._

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

  def allBuildsStream[F[_]: ConcurrentEffect: Timer](
    scraper: Scraper[F],
    rate: FiniteDuration
  ): Stream[F, Stream[F, Either[ScraperError, Build]]] =
    Stream.emits(Branch.all).map { branch =>
      eagerAwakeEvery(rate)
        .zipRight(branch.buildStream(scraper))
        .evalTap {
          case Left(error) =>
            Logger[F].error(error)(show"failed to scrape $branch")
          case Right(build) =>
            Logger[F].info(show"scraped $branch: $build")
        }
    }

  def program[F[_]: ConcurrentEffect: Timer]: F[Unit] =
    Stream
      .resource(Scraper.global[F])
      .flatMap { scraper =>
        allBuildsStream(scraper, rate = 5.seconds)
          .parJoin(Branch.all.size)
      }
      .compile
      .drain

  override def run(args: List[String]): IO[ExitCode] = {
    Logger[IO].info("starting scrape loop") *> program[IO].as(ExitCode.Success)
  }
}

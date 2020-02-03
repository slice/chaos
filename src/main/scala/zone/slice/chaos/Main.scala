package zone.slice.chaos

import discord._
import scraper._
import scraper.errors._

import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import scala.concurrent.duration._
import fs2._

object Main extends IOApp {
  def print[F[_]: Sync](text: String): F[Unit] = Sync[F].delay(println(text))

  def allBuildsStream[F[_]: ConcurrentEffect: Timer](
    scraper: Scraper[F],
    rate: FiniteDuration
  ): Stream[F, Stream[F, Either[ScraperError, Build]]] =
    Stream.emits(Branch.all).map { branch =>
      branch
        .buildStream(scraper)
        .metered(rate)
        .evalTap {
          case Left(error) =>
            print[F](show"[error] failed to scrape $branch: $error")
          case Right(build) =>
            print[F](show"[info] scraped $branch: $build")
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
    IO(println("[info] scraper started")) *> program[IO].as(ExitCode.Success)
  }
}

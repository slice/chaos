package zone.slice.chaos

import discord._
import scraper._

import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import scala.concurrent.duration._
import fs2._

object Main extends IOApp {
  def print[F[_]: Sync](text: String): F[Unit] = Sync[F].delay(println(text))

  def program[F[_]: ConcurrentEffect: Timer]: F[Unit] =
    Stream.emits(Branch.all)
      .map { branch =>
        branch.buildStream(Scraper.global[F])
          .metered(1.second)
          .evalTap(build => print[F](s"Scraped $branch: $build"))
      }
      .parJoin(Branch.all.size)
      .compile
      .drain

  override def run(args: List[String]): IO[ExitCode] = {
    program[IO].as(ExitCode.Success)
  }
}

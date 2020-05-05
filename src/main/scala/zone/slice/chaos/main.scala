package zone.slice.chaos

import poller._

import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.DecodingFailure
import io.circe.config.parser
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.config.syntax._

object Main extends IOApp {
  protected implicit def unsafeLogger[F[_]: Sync]: Logger[F] =
    Slf4jLogger.getLogger[F]

  implicit val circeConfiguration: Configuration =
    Configuration.default.withDefaults.withSnakeCaseMemberNames

  def eput[F[_]: Sync](message: String): F[Unit] =
    Sync[F].delay(Console.err.println(message))

  def handleConfigError[F[_]: Sync](throwable: Throwable): F[Unit] =
    throwable match {
      case decodingFailure: DecodingFailure =>
        eput(show"Failed to decode config file: $decodingFailure")
      case error =>
        eput(s"Failed to load config file: $error")
    }

  def program[F[_]: ConcurrentEffect: Timer: ContextShift](
      implicit UM: cats.Monoid[F[Unit]],
  ): F[ExitCode] =
    parser
      .decodeF[F, Config]()
      .attemptT
      .foldF(
        handleConfigError(_).as(ExitCode.Error),
        Poller(_).as(ExitCode.Success),
      )

  override def run(args: List[String]): IO[ExitCode] =
    program[IO]
}

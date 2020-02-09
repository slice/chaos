package zone.slice.chaos
package publisher

import discord.{Build, Webhook}
import DiscordPublisher._

import cats.implicits._
import cats.data.EitherT
import cats.effect.Sync
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.Method._
import org.http4s.circe._
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Status
import io.circe._

class DiscordPublisher[F[_]: Sync](webhook: Webhook, httpClient: Client[F])
    extends Publisher[F, DiscordPublisherError]
    with Http4sClientDsl[F] {

  protected implicit def unsafeLogger: Logger[F] =
    Slf4jLogger.getLogger[F]

  override def publish(
    build: Build
  ): EitherT[F, DiscordPublisherError, Unit] = {
    val description = build.assets
      .map(asset => s"[`${asset.filename}`](${asset.uri})")
      .mkString("\n")

    val str = Json.fromString _
    val body: Json = Json.obj(
      "username" -> str("chaos"),
      "embeds" -> Json.arr(
        Json.obj(
          "title" -> str(s"${build.branch} ${build.buildNumber}"),
          "description" -> str(description),
          "color" -> Json.fromInt(build.branch.color)
        )
      )
    )

    val request = httpClient
      .expect[Unit](POST(body, webhook.uri, Headers.userAgentHeader))
      .attemptT
      .leftMap[DiscordPublisherError] {
        case UnexpectedStatus(status) => DiscordApiError(status)
        case throwable                => NetworkError(throwable)
      }

    val message =
      show"Publishing ${build.branch} ${build.buildNumber} to Discord webhook ${webhook.id}"
    for {
      _ <- EitherT.right(Logger[F].info(message))
      _ <- request
    } yield ()
  }
}

object DiscordPublisher {
  sealed trait DiscordPublisherError extends Exception
  final case class DiscordApiError(status: Status)
      extends DiscordPublisherError {
    override def getMessage: String =
      s"Failed to publish to Discord: ${status.code} ${status.reason}"
  }
  final case class NetworkError(error: Throwable)
      extends DiscordPublisherError {
    override def getMessage: String =
      s"Failed to send request to Discord: ${error.getMessage}"
  }
}

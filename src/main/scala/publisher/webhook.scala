package zone.slice.chaos
package publisher

import discord._
import source.Headers

import cats.implicits._
import cats.data.Kleisli
import cats.effect.Sync
import org.http4s.circe._
import org.http4s.Uri
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.Method._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import io.circe._
import io.circe.literal._

case class WebhookPublisher[F[_]: Sync](endpoint: Uri, httpClient: Client[F])
    extends Publisher[F]
    with Http4sClientDsl[F] {
  protected implicit def unsafeLogger: Logger[F] =
    Slf4jLogger.getLogger[F]

  def deployJson(deploy: Deploy): Json =
    deploy.build match {
      case build: HostBuild => json"""{"type": "host", "build": $build}"""
      case build: FrontendBuild => json"""{"type": "fe", "build": $build}"""
    }

  override val publish = Kleisli { deploy =>
    for {
      _ <- Logger[F].info(
        show"Submitting ${deploy.build.branch} to webhook at ${endpoint.renderString}",
      )
      request = POST(deployJson(deploy), endpoint, Headers.userAgentHeader)
      _ <- httpClient.expect[Unit](request)
    } yield ()
  }
}

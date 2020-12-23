package zone.slice.chaos
package publisher

import discord._
import source.Headers

import cats.implicits._
import cats.effect.Sync
import org.http4s.circe._
import org.http4s.Uri
import org.http4s.Method._
import org.http4s.client.Client
import io.chrisdavenport.log4cats.Logger
import io.circe._
import io.circe.literal._

case class WebhookPublisher[F[_]: Sync](endpoint: Uri, httpClient: Client[F])
    extends HTTPPublisher[F] {
  def deployJson(deploy: Deploy): Json =
    deploy.build match {
      case build: HostBuild     => json"""{"type": "host", "build": $build}"""
      case build: FrontendBuild => json"""{"type": "fe", "build": $build}"""
      case build: CourgetteBuild =>
        json"""{"type": "courgette", "build": $build}"""
    }

  override def publish(deploy: Deploy): F[Unit] =
    for {
      _ <- Logger[F].info(
        show"Submitting ${deploy.build.branch} to webhook at ${endpoint.renderString}",
      )
      request = POST(deployJson(deploy), endpoint, Headers.userAgentHeader)
      _ <- httpClient.expect[Unit](request)
    } yield ()
}

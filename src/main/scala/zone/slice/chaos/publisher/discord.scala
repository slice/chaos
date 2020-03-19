package zone.slice.chaos
package publisher

import discord._
import source.Headers

import cats.implicits._
import cats.effect.Sync
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.Method._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import io.circe._
import io.circe.literal._

import java.time.Instant

case class DiscordPublisher[F[_]: Sync](webhook: Webhook, httpClient: Client[F])
    extends Publisher[F]
    with Http4sClientDsl[F] {

  protected implicit def unsafeLogger: Logger[F] =
    Slf4jLogger.getLogger[F]

  protected def assetList(assets: Vector[Asset]): Vector[String] =
    assets.map(asset => s"[`${asset.filename}`](${asset.uri})")

  private def currentTimestamp: F[String] =
    Sync[F].delay {
      Instant.now().toString
    }

  protected def labelScriptList(scripts: Vector[String]): Vector[String] = {
    val assumedNames = List("chunk loader", "classes", "vendor", "entrypoint")

    if (scripts.size == assumedNames.size) {
      // Only attempt to label each script if we get the expected amount of
      // scripts.
      scripts.zip(assumedNames).map {
        case (link, name) => s"$link ($name)"
      }
    } else scripts
  }

  protected def embedForDeploy(deploy: Deploy): F[Json] = {
    val build = deploy.build

    val title =
      if (deploy.isRevert)
        show"\u21a9\ufe0f ${build.branch} reverted to ${build.number}"
      else show"${build.branch} ${build.number}"
    val description = s"Hash: `${build.hash}`"

    val scriptList =
      labelScriptList(assetList(build.assets.scripts)).mkString("\n")
    val stylesheetList = assetList(build.assets.stylesheets).mkString("\n")

    currentTimestamp map { timestamp =>
      json"""
      {
        "title": $title,
        "color": ${build.branch.color},
        "description": $description,
        "fields": [
          {"name": "Scripts", "value": $scriptList},
          {"name": "Stylesheets", "value": $stylesheetList}
        ],
        "timestamp": $timestamp
      }
      """
    } map { embed =>
      json"""
      {
        "username": "chaos",
        "embeds": [$embed]
      }
      """
    }
  }

  override def publish(deploy: Deploy): F[Unit] = {
    val message =
      show"Publishing ${deploy.build.branch} ${deploy.build.number} to Discord webhook ${webhook.id}"
    for {
      _     <- Logger[F].info(message)
      embed <- embedForDeploy(deploy)
      request = POST(embed, webhook.uri, Headers.userAgentHeader)
      _ <- httpClient.expect[Unit](request)
    } yield ()
  }
}

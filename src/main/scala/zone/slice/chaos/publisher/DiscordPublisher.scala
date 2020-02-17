package zone.slice.chaos
package publisher

import discord.{Build, Webhook, Asset}
import scraper.Headers

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

class DiscordPublisher[F[_]: Sync](webhook: Webhook, httpClient: Client[F])
    extends Publisher[F]
    with Http4sClientDsl[F] {

  protected implicit def unsafeLogger: Logger[F] =
    Slf4jLogger.getLogger[F]

  protected def assetList(assets: Vector[Asset]): Vector[String] =
    assets.map(asset => s"[`${asset.filename}`](${asset.uri})")

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

  protected def embedForBuild(build: Build): Json = {
    val title = show"${build.branch} ${build.buildNumber}"

    val scriptList =
      labelScriptList(assetList(build.assets.scripts)).mkString("\n")
    val stylesheetList = assetList(build.assets.stylesheets).mkString("\n")

    val embed = json"""
      {
        "title": $title,
        "color": ${build.branch.color},
        "fields": [
          {"name": "Scripts", "value": $scriptList},
          {"name": "Stylesheets", "value": $stylesheetList}
        ]
      }
    """

    json"""
      {
        "username": "chaos",
        "embeds": [$embed]
      }
    """
  }

  override def publish(build: Build): F[Unit] = {
    val message =
      show"Publishing ${build.branch} ${build.buildNumber} to Discord webhook ${webhook.id}"
    for {
      _ <- Logger[F].info(message)
      request = POST(embedForBuild(build), webhook.uri, Headers.userAgentHeader)
      _ <- httpClient.expect[Unit](request)
    } yield ()
  }
}

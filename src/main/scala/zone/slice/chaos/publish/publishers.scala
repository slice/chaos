package zone.slice.chaos
package publish

import cats.effect.Concurrent
import org.http4s.Uri
import org.http4s.circe._
import _root_.io.circe.Json
import _root_.io.circe.literal._

import discord._

object publishers {
  def printPublisher[F[_]](prefix: String): Publisher[F, FeBuild] =
    (b: FeBuild, p: Publish[F]) => p.output(s"$prefix: $b")

  def discordWebhookPublisher[F[_]: Concurrent](
    webhook: Uri,
  ): Publisher[F, FeBuild] =
    (b: FeBuild, p: Publish[F]) => {
      val embed = json"""
        {
          "title": ${s"${b.branch.humanName} ${b.number}"},
          "description": ${s"Hash: `${b.hash}`"},
          "color": ${b.branch.color}
        }
      """
      val body = json"""{"embeds": [$embed]}"""

      p.post[Json, Unit](webhook, body)
    }
}

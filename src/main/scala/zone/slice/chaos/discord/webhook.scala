package zone.slice.chaos
package discord

import org.http4s.Uri

/** A Discord webhook. */
case class Webhook(id: BigInt, token: String) {

  /** The http4s Uri of this webhook. */
  def uri: Uri =
    Uri.unsafeFromString(s"https://discordapp.com/api/webhooks/$id/$token")
}

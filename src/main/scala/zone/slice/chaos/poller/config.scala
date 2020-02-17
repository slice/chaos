package zone.slice.chaos
package poller

import discord.Branch

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.Decoder

import scala.concurrent.duration.FiniteDuration

sealed trait PublisherSetting {

  /** A set of branches to only builds publish from. */
  def branches: Set[Branch]
}

object PublisherSetting {
  implicit val circeConfiguration: Configuration =
    Configuration.default.withDefaults

  implicit val decodePublisherSetting: Decoder[PublisherSetting] =
    Decoder.instance { cursor =>
      cursor.downField("type").as[String].flatMap {
        case "discord" => cursor.as[DiscordPublisherSetting]
        case "stdout"  => cursor.as[StdoutPublisherSetting]
      }
    }
}

final case class StdoutPublisherSetting(
    format: String,
    branches: Set[Branch] = Branch.all,
) extends PublisherSetting
final case class DiscordPublisherSetting(
    id: BigInt,
    token: String,
    branches: Set[Branch] = Branch.all,
) extends PublisherSetting

/** A configuration for a [[Poller]]. */
case class Config(interval: FiniteDuration, publishers: List[PublisherSetting])

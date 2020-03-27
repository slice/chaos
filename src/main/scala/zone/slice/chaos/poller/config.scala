package zone.slice.chaos
package poller

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.Decoder

import scala.concurrent.duration.FiniteDuration

sealed trait PublisherSetting {

  /** A set of scraping identifiers to scrape from. */
  def scrape: Set[String]
}

object PublisherSetting {
  implicit val circeConfiguration: Configuration =
    Configuration.default.withDefaults.withSnakeCaseMemberNames

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
    scrape: Set[String],
) extends PublisherSetting
final case class DiscordPublisherSetting(
    id: BigInt,
    token: String,
    scrape: Set[String],
) extends PublisherSetting

/** A configuration for a [[Poller]]. */
case class Config(
    interval: FiniteDuration,
    publishers: List[PublisherSetting],
    stateFilePath: String = "./state.chaos",
)

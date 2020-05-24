package zone.slice.chaos
package poller

import scala.util.Try

import org.http4s.Uri
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.Decoder

import scala.concurrent.duration._

sealed trait PublisherSetting {

  /** A set of scraping identifiers to scrape from. */
  def scrape: Set[String]
}

object PublisherSetting {
  implicit val circeConfiguration: Configuration =
    Configuration.default.withDefaults.withSnakeCaseMemberNames

  implicit val decodeUri: Decoder[Uri] =
    Decoder.decodeString.emapTry { str => Try(Uri.unsafeFromString(str)) }

  implicit val decodePublisherSetting: Decoder[PublisherSetting] =
    Decoder.instance { cursor =>
      cursor.downField("type").as[String].flatMap {
        case "discord" => cursor.as[DiscordPublisherSetting]
        case "stdout"  => cursor.as[StdoutPublisherSetting]
        case "webhook" => cursor.as[WebhookPublisherSetting]
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
final case class WebhookPublisherSetting(
    uri: Uri,
    scrape: Set[String],
) extends PublisherSetting

/** A configuration for a [[Poller]]. */
case class Config(
    interval: FiniteDuration = 1.minute,
    requestRatelimit: FiniteDuration = 1.second,
    publishers: List[PublisherSetting] = List(),
    stateFilePath: String = "./state.chaos",
)

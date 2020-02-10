package zone.slice.chaos

import io.circe.generic.auto._
import io.circe.Decoder

import scala.concurrent.duration.FiniteDuration

sealed trait PublisherSetting

object PublisherSetting {
  implicit val decodePublisherSetting: Decoder[PublisherSetting] =
    Decoder.instance { cursor =>
      cursor.downField("type").as[String].flatMap {
        case "discord" => cursor.as[DiscordPublisherSetting]
      }
    }
}

final case class DiscordPublisherSetting(id: BigInt, token: String)
    extends PublisherSetting

case class Config(interval: FiniteDuration, publishers: List[PublisherSetting])

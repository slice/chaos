package zone.slice.chaos.config

import pureconfig.generic.semiauto._
import scala.concurrent.duration._

sealed abstract class PublisherConfig extends Product with Serializable {
  def scrape: Vector[String]
}

object PublisherConfig {
  final case class Discord(url: String, scrape: Vector[String])
      extends PublisherConfig
  object Discord {
    implicit val reader = deriveReader[Discord]
  }

  final case class Print(format: String, scrape: Vector[String])
      extends PublisherConfig
  object Print {
    implicit val reader = deriveReader[Print]
  }

  implicit val reader = deriveReader[PublisherConfig]
}

case class ChaosConfig(
  interval: FiniteDuration,
  publishers: Vector[PublisherConfig],
)

object ChaosConfig {
  implicit val reader = deriveReader[ChaosConfig]
}

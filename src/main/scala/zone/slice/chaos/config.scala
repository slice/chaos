package zone.slice.chaos.config

import org.http4s.Uri
import fs2.io.file.Path
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto._
import scala.concurrent.duration._

sealed abstract class PublisherConfig extends Product with Serializable {
  def scrape: Vector[String]
}

object PublisherConfig {
  implicit val uriReader = ConfigReader.fromCursor[Uri] { cursor =>
    cursor.asString.flatMap { string =>
      Uri.fromString(string) match {
        case Left(error) =>
          cursor.failed(CannotConvert(string, "uri", error.message))
        case Right(uri) => Right(uri)
      }
    }
  }

  final case class Discord(uri: Uri, scrape: Vector[String])
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
  stateFilePath: Path,
  publishers: Vector[PublisherConfig],
)

object ChaosConfig {
  implicit val pathReader = ConfigReader[String].map(Path.apply)
  implicit val reader     = deriveReader[ChaosConfig]
}

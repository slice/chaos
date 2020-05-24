package zone.slice.chaos
package discord

import poller._

import io.circe._
import io.circe.generic.semiauto._
import cats._

/** A platform for which Discord host builds are deployed. */
sealed trait Platform {

  /** The platform identifier; passed as a query argument to the updates
    * endpoint, like so: `https://discordapp.com/api/v7/updates/canary?platform=win`
    */
  def identifier: String

  /** A human-friendly name for the platform. */
  def name: String
}

object Platform {
  case object Windows extends Platform {
    val identifier = "win"
    val name       = "Windows"
  }
  case object Mac extends Platform {
    val identifier = "osx"
    val name       = "macOS"
  }
  case object Linux extends Platform {
    val identifier = "linux"
    val name       = "Linux"
  }

  /** The set of all platforms. */
  def all: Set[Platform] = Set(Windows, Mac, Linux)

  implicit val platformEncoder: Encoder[Platform] = deriveEncoder

  implicit def showPlatform: Show[Platform] =
    (plat: Platform) => s"Platform(${plat.identifier})"

  implicit def selectPlatform: Select[Platform] =
    new Select[Platform] {
      val all: Map[String, Platform] = Map(
        "win"   -> Windows,
        "osx"   -> Mac,
        "linux" -> Linux,
      )

      override val aliases: Map[String, String] = Map(
        "mac"     -> "osx",
        "macos"   -> "osx",
        "windows" -> "win",
      )
    }
}

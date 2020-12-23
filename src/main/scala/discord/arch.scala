package zone.slice.chaos
package discord

import poller.Select

import io.circe._
import cats.Show

sealed trait Arch

object Arch {
  case object X86   extends Arch
  case object X64   extends Arch
  case object Armv7 extends Arch
  case object Armv8 extends Arch

  implicit val showArch: Show[Arch] = Show.fromToString

  implicit val encodeArch: Encoder[Arch] = Encoder.instance {
    case X86   => Json.fromString("x86")
    case X64   => Json.fromString("x64")
    case Armv7 => Json.fromString("armv7")
    case Armv8 => Json.fromString("armv8")
  }

  implicit val decodeArch: Decoder[Arch] = Decoder.instance { cursor =>
    cursor.as[String].flatMap {
      case "x86"   => Right(X86)
      case "x64"   => Right(X64)
      case "armv7" => Right(Armv7)
      case "armv8" => Right(Armv8)
      case other =>
        Left(
          DecodingFailure(s"$other is not a valid architecture", cursor.history),
        )
    }
  }

  implicit val selectArch: Select[Arch] = new Select[Arch] {
    def all: Map[String, Arch] =
      Map(
        "x86"   -> Arch.X86,
        "x64"   -> Arch.X64,
        "armv7" -> Arch.Armv7,
        "armv8" -> Arch.Armv8,
      )
  }
}

package zone.slice.chaos
package discord

import select._

import cats.syntax.all._
import cats.Eq
import org.http4s.Uri

sealed abstract class Branch extends Product with Serializable {
  import Branch._

  def color: Int =
    this match {
      case Stable      => 0x7289da
      case Ptb         => 0x99aab5
      case Canary      => 0xf1c40f
      case Development => 0x333333
    }

  def humanName: String =
    if (this === Ptb) "PTB" else this.toString

  def hasFrontend: Boolean =
    this =!= Development

  def subdomain: Option[String] =
    this match {
      case Stable      => none
      case Ptb         => "ptb".some
      case Canary      => "canary".some
      case Development => none
    }

  def uri: Uri = {
    val squished = subdomain.map(_ + ".").getOrElse("")
    Uri.unsafeFromString(s"https://${squished}discord.com")
  }

  def clientUri: Uri =
    uri / "channels" / "@me"
}

object Branch {
  final case object Stable      extends Branch
  final case object Ptb         extends Branch
  final case object Canary      extends Branch
  final case object Development extends Branch

  implicit val eqBranch: Eq[Branch] = Eq.fromUniversalEquals

  implicit val selectBranch: Select[Branch] = Select.fromPartialFunction {
    case "s" | "stable"              => Stable
    case "p" | "ptb"                 => Ptb
    case "c" | "canary"              => Canary
    case "d" | "dev" | "development" => Development
  }
}

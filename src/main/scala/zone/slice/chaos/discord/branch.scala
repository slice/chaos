package zone.slice.chaos
package discord

import cats.syntax.all._
import cats.Eq
import org.http4s.Uri

sealed trait Branch {
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
  case object Stable      extends Branch
  case object Ptb         extends Branch
  case object Canary      extends Branch
  case object Development extends Branch

  implicit val eqBranch: Eq[Branch] = Eq.fromUniversalEquals
}

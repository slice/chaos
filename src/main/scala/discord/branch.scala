package zone.slice.chaos
package discord

import io.circe._
import cats.Show
import org.http4s.Uri
import org.http4s.implicits._

/** A Discord client branch. */
sealed trait Branch {

  /** The `discordapp.com` subdomain of this branch. */
  def subdomain: Option[String]

  /** The http4s URI of this branch. */
  def uri: Uri =
    subdomain match {
      case Some(subdomain) =>
        Uri.unsafeFromString(s"https://$subdomain.discordapp.com")
      case None => uri"https://discordapp.com"
    }

  /** The "color" of this branch. */
  def color: Int =
    this match {
      case Branch.Stable => 0x7289da
      case Branch.PTB    => 0x99aab5
      case Branch.Canary => 0xf1c40f
    }
}

object Branch {

  /** The main branch. */
  final case object Stable extends Branch {
    override def subdomain: Option[String] = None
  }

  /** The "Public Test Build" branch. */
  final case object PTB extends Branch {
    override def subdomain: Option[String] = Some("ptb")
  }

  /** The "canary" branch. Typically has the latest changes. */
  final case object Canary extends Branch {
    override def subdomain: Option[String] = Some("canary")
  }

  /** The list of all branches. */
  val all: Set[Branch] = Set(Stable, PTB, Canary)

  implicit val showBranch: Show[Branch] = Show.fromToString[Branch]

  implicit val encodeBranch: Encoder[Branch] = Encoder.instance {
    case Canary => Json.fromString("canary")
    case PTB => Json.fromString("ptb")
    case Stable => Json.fromString("stable")
  }

  implicit val decodeBranch: Decoder[Branch] = Decoder.instance { cursor =>
    cursor.as[String].flatMap {
      case "canary" => Right(Canary)
      case "ptb"    => Right(PTB)
      case "stable" => Right(Stable)
      case other =>
        Left(DecodingFailure(s"$other is not a Discord branch", cursor.history))
    }
  }
}

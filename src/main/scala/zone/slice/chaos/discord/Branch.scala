package zone.slice.chaos
package discord

import scraper._
import fs2._
import cats.effect._
import org.http4s.Uri
import org.http4s.implicits._

/** A Discord client branch. */
sealed trait Branch {
  /** The `discordapp.com` subdomain of this branch. */
  def subdomain: Option[String]

  /** The [[org.http4s.Uri URI]] of this branch. */
  def uri: Uri =
    subdomain match {
      case Some(subdomain) => Uri.unsafeFromString(s"https://$subdomain.discordapp.com")
      case None => uri"https://discordapp.com"
    }

  /** The [[fs2.Stream stream]] of builds for this branch. */
  def buildStream[F[_]: Concurrent](scraperResource: Resource[F, Scraper[F]]): Stream[F, Build] =
    Stream.resource(scraperResource).flatMap { scraper =>
      Stream.repeatEval(scraper.scrape(this))
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
  val all: List[Branch] = Stable :: PTB :: Canary :: Nil
}

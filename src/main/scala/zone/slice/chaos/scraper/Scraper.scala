package zone.slice.chaos
package scraper

import discord._

import cats.effect._
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext

/**
 * Downloads Discord client bundles and extracts metdata from them (in the form
 * of [[discord.Build]] objects).
 *
 * Effects are executed within `F`.
 */
trait Scraper[F[_]] {
  /** Scrapes a [[discord.Branch branch]], yielding [[discord.Build build]] information. */
  def scrape(branch: Branch): F[Build]
}

object Scraper {
  /** Creates an Http4s-based scraper from an execution context. */
  def apply[F[_]: ConcurrentEffect](ec: ExecutionContext): Resource[F, Scraper[F]] =
    BlazeClientBuilder[F](ec).resource.map(new Http4sScraper[F](_))

  /** Creates an Http4s-based scraper from the global execution context. */
  def global[F[_]: ConcurrentEffect]: Resource[F, Scraper[F]] =
    apply(ExecutionContext.global)
}

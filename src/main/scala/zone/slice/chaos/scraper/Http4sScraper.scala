package zone.slice.chaos
package scraper

import discord._
import cats.implicits._
import cats.effect._
import org.http4s.client.Client

/**
 * A [[Scraper]] which uses an http4s Client to perform HTTP requests.
 */
class Http4sScraper[F[_]](client: Client[F])(implicit F: Sync[F]) extends Scraper[F] {
  override def scrape(branch: Branch): F[Build] =
    // ... sample implementation ...
    F.delay(println(s"Scraping $branch!")).as(Build(branch = branch, buildNumber = 1))
}

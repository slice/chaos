package zone.slice.chaos
package scraper

import discord._
import errors._

import cats.data.EitherT
import cats.effect._
import cats.implicits._
import cats._
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext

/**
  * Downloads Discord client bundles and extracts metdata from them (in the form
  * of [[discord.Build]] objects).
  *
  * Effects are executed within `F`.
  */
trait Scraper[F[_]] {

  /** Downloads the HTML for the `/channels/@me` of the branch. */
  def download(branch: Branch): EitherT[F, DownloadError, String]

  /**
    * Scrapes a [[discord.Branch branch]], yielding [[discord.Build build]] information.
    * This combines `download` and `extract` into one method.
    */
  def scrape(
    branch: Branch
  )(implicit monad: Monad[F]): EitherT[F, ScraperError, Build] = {
    download(branch)
      .leftMap(ScraperError.Download)
      .leftWiden[ScraperError]
      .map(pageText => Build(branch = branch, buildNumber = 1)) // TODO: Write the extractor
  }
}

object Scraper {

  /** Creates an Http4s-based scraper from an execution context. */
  def apply[F[_]: ConcurrentEffect](
    ec: ExecutionContext
  ): Resource[F, Scraper[F]] =
    BlazeClientBuilder[F](ec).resource.map(new Http4sScraper[F](_))

  /** Creates an Http4s-based scraper from the global execution context. */
  def global[F[_]: ConcurrentEffect]: Resource[F, Scraper[F]] =
    apply(ExecutionContext.global)
}

package zone.slice.chaos
package scraper

import discord._
import errors._
import cats.data.EitherT
import cats.effect._
import cats.implicits._
import cats._
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext
import scala.util.matching.Regex

/**
  * Downloads Discord client bundles and extracts metdata from them (in the form
  * of [[discord.Build]] objects).
  *
  * Effects are executed within `F`.
  */
trait Scraper[F[_]] {

  /** Downloads the content of a Uri as a [[String]]. */
  protected def fetch(uri: Uri): EitherT[F, DownloadError, String]

  /** Downloads the main client HTML for a [[Branch]]. */
  def fetchClient(branch: Branch): EitherT[F, DownloadError, String] =
    fetch(branch.uri / "channels" / "@me")

  /** Extracts resources (scripts and styles) from the HTML of `/channels/@me`. */
  def extract(branch: Branch,
              pageHtml: String): Either[ExtractorError, Build] = {
    val scriptTagRegex =
      raw"""<script src="/assets/([.a-f0-9]+)\.js" integrity=".+"></script>""".r.unanchored
    val styleTagRegex =
      raw"""<link rel="stylesheet" href="/assets/([.a-f0-9]+)\.css" integrity=".+">""".r.unanchored

    def pull[A](maker: String => A,
                error: ExtractorError,
                regex: Regex): Either[ExtractorError, Vector[A]] = {
      val hashes = regex.findAllMatchIn(pageHtml).map(_.group(1))
      if (hashes.isEmpty) Left(error)
      else Right(hashes.map(maker).toVector)
    }

    for {
      scripts <- pull(Asset.Script, NoScripts, scriptTagRegex)
      styles <- pull(Asset.Stylesheet, NoStylesheets, styleTagRegex)
    } yield Build(branch = branch, buildNumber = 1, assets = scripts ++ styles)
  }

  /**
    * Scrapes a [[discord.Branch branch]], yielding [[discord.Build build]] information.
    * This combines `download` and `extract` into one method.
    */
  def scrape(
    branch: Branch
  )(implicit monad: Monad[F]): EitherT[F, ScraperError, Build] = {
    for {
      pageText <- fetchClient(branch)
        .leftMap[ScraperError](ScraperError.Download)
      build <- EitherT.fromEither[F](
        extract(branch, pageText)
          .leftMap[ScraperError](ScraperError.Extractor)
      )
    } yield build
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

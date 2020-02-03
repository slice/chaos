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
import scala.util.matching.{Regex, UnanchoredRegex}

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

  /** Extracts assets (scripts and styles) from the HTML of `/channels/@me`. */
  def extractAssets(pageHtml: String): Either[ExtractorError, Vector[Asset]] = {
    val scriptTagRegex =
      raw"""<script src="/assets/([.a-f0-9]+)\.js" integrity="[^"]+"></script>""".r.unanchored
    val styleTagRegex =
      raw"""<link rel="stylesheet" href="/assets/([.a-f0-9]+)\.css" integrity="[^"]+">""".r.unanchored

    def pull[A <: Asset](assetType: String => A,
                         extractorError: ExtractorError,
                         regex: Regex): Either[ExtractorError, Vector[A]] = {
      val hashes = regex.findAllMatchIn(pageHtml).map(_.group(1))
      if (hashes.isEmpty) Left(extractorError)
      else Right(hashes.map(assetType).toVector)
    }

    val assetTypes: Map[String => Asset, (UnanchoredRegex, ExtractorError)] =
      Map(
        Asset.Script -> (scriptTagRegex, NoScripts),
        Asset.Stylesheet -> (styleTagRegex, NoStylesheets)
      )

    assetTypes
      .map {
        case (assetType, (regex, extractorError)) =>
          pull(assetType, extractorError, regex)
      }
      .toVector
      .sequence
      .map(_.flatten)
  }

  /** Fetches and extracts the build number from a [[Seq]] of [[Asset]]s. */
  def fetchBuildNumber(branch: Branch, assets: Seq[Asset])(
    implicit monad: Monad[F]
  ): EitherT[F, ScraperError, Int] = {
    val scripts = assets.filter(_.isInstanceOf[Asset.Script])
    val buildMetadataRegex =
      raw"Build Number: (\d+), Version Hash: ([a-f0-9]+)".r.unanchored

    for {
      mainScript <- EitherT
        .fromEither[F](scripts.lastOption.toRight(NoScripts))
        .leftMap[ScraperError](ScraperError.Extractor)
      text <- fetch(branch.uri / "assets" / mainScript.filename.path)
        .leftMap[ScraperError](ScraperError.Download)
      buildNumber <- EitherT
        .fromEither[F](
          buildMetadataRegex
            .findFirstMatchIn(text)
            .toRight(NoBuildNumber)
        )
        .leftMap[ScraperError](ScraperError.Extractor)
        .map(_.group(1).toInt)
    } yield buildNumber
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
      assets <- EitherT.fromEither[F](
        extractAssets(pageText)
          .leftMap[ScraperError](ScraperError.Extractor)
      )
      buildNumber <- fetchBuildNumber(branch, assets)
    } yield Build(branch = branch, buildNumber = buildNumber, assets = assets)
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

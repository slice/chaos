package zone.slice.chaos
package scraper

import discord._
import errors._

import cats.data.EitherT
import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.{MessageFailure, Request, Uri}
import org.http4s.client.{Client, UnexpectedStatus}

import scala.util.matching.{Regex, UnanchoredRegex}

/**
  * Downloads Discord client bundles and extracts metdata from them (in the form
  * of [[discord.Build]] objects).
  *
  * Effects are executed within `F`.
  */
class Scraper[F[_]: Sync](val httpClient: Client[F]) {

  protected implicit def unsafeLogger: Logger[F] =
    Slf4jLogger.getLogger[F]

  /** Downloads the content of a Uri as a [[String]]. */
  protected def fetch(uri: Uri): EitherT[F, DownloadError, String] = {
    val request = Request[F](uri = uri, headers = Headers.headers)

    for {
      _ <- EitherT.right(Logger[F].debug(s"GETting $uri"))
      text <- httpClient
        .expect[String](request)
        .attemptT
        .leftMap[DownloadError] {
          case UnexpectedStatus(status) => DownloadError.HTTPError(status)
          case failure: MessageFailure  => DownloadError.DecodeError(failure)
          case throwable                => DownloadError.NetworkError(throwable)
        }
    } yield text
  }

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
        Asset.Script -> ((scriptTagRegex, ExtractorError.NoScripts)),
        Asset.Stylesheet -> ((styleTagRegex, ExtractorError.NoStylesheets))
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
  def fetchBuildNumber(branch: Branch,
                       assets: Seq[Asset]): EitherT[F, ScraperError, Int] = {
    val scripts = assets.filter(_.isInstanceOf[Asset.Script])
    val buildMetadataRegex =
      raw"Build Number: (\d+), Version Hash: ([a-f0-9]+)".r.unanchored

    for {
      mainScript <- EitherT
        .fromEither[F](scripts.lastOption.toRight(ExtractorError.NoScripts))
        .leftMap[ScraperError](ScraperError.Extractor)
      text <- fetch(branch.uri / "assets" / mainScript.filename.path)
        .leftMap[ScraperError](ScraperError.Download)
      buildNumber <- EitherT
        .fromEither[F](
          buildMetadataRegex
            .findFirstMatchIn(text)
            .toRight(ExtractorError.NoBuildNumber)
        )
        .leftMap[ScraperError](ScraperError.Extractor)
        .map(_.group(1).toInt)
    } yield buildNumber
  }

  /**
    * Scrapes a [[discord.Branch Discord branch]] for [[discord.Build build information]].
    *
    * This takes care of downloading the branch's HTML, finding [[discord.Asset assets]],
    * extracting the build number, etc.
    */
  def scrape(branch: Branch): EitherT[F, ScraperError, Build] = {
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

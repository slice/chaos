package zone.slice.chaos
package scraper

import discord._
import errors._

import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.{Request, Uri}
import org.http4s.client.Client

import scala.util.matching.Regex

/**
  * Downloads Discord client bundles and extracts metdata from them (in the form
  * of [[discord.Build]] objects).
  *
  * Effects are executed within `F`.
  */
class Scraper[F[_]](val httpClient: Client[F])(implicit F: Sync[F]) {

  protected implicit def unsafeLogger: Logger[F] =
    Slf4jLogger.getLogger[F]

  /** Downloads the content of a Uri as a string. */
  protected def fetch(uri: Uri): F[String] = {
    val request = Request[F](uri = uri, headers = Headers.headers)
    Logger[F].debug(s"GETting $uri") *> httpClient.expect[String](request)
  }

  /** Downloads the main client HTML for a [[discord.Branch]]. */
  def fetchClient(branch: Branch): F[String] =
    fetch(branch.uri / "channels" / "@me")

  /** Extracts assets (scripts and styles) from the HTML of `/channels/@me`. */
  def extractAssets(pageHtml: String): F[AssetBundle] = {
    val scriptTagRegex =
      """<script src="/assets/([.a-f0-9]+)\.js" integrity="[^"]+"></script>""".r.unanchored
    val styleTagRegex =
      """<link rel="stylesheet" href="/assets/([.a-f0-9]+)\.css" integrity="[^"]+">""".r.unanchored

    def pull[A <: Asset](creator: String => A,
                         notFoundException: Exception,
                         regex: Regex): F[Vector[A]] = {
      val hashes = regex.findAllMatchIn(pageHtml).map(_.group(1))
      if (hashes.isEmpty) F.raiseError(notFoundException)
      else F.pure(hashes.map(creator).toVector)
    }

    (
      pull(Asset.Script, ExtractorError.NoScripts, scriptTagRegex),
      pull(Asset.Stylesheet, ExtractorError.NoStylesheets, styleTagRegex)
    ).tupled.map {
      case (scripts, stylesheets) => AssetBundle(scripts, stylesheets)
    }
  }

  /** Fetches and extracts the build number from an [[discord.AssetBundle]]. */
  def fetchBuildNumber(assets: AssetBundle): F[Int] = {
    val buildMetadataRegex =
      """Build Number: (\d+), Version Hash: ([a-f0-9]+)""".r.unanchored

    import ExtractorError._
    for {
      mainScript <- F.fromOption(assets.scripts.lastOption, NoScripts)
      text <- fetch(mainScript.uri)
      buildNumberOption = buildMetadataRegex
        .findFirstMatchIn(text)
        .map(_.group(1).toInt)
      buildNumber <- F.fromOption(buildNumberOption, NoBuildNumber)
    } yield buildNumber
  }

  /**
    * Scrapes a [[discord.Branch]] for [[discord.Build build information]].
    *
    * This takes care of downloading the branch's HTML, finding
    * [[discord.Asset]]s, extracting the build number, etc.
    */
  def scrape(branch: Branch): F[Build] = {
    for {
      pageText <- fetchClient(branch)
      assetBundle <- extractAssets(pageText)
      buildNumber <- fetchBuildNumber(assetBundle)
    } yield
      Build(branch = branch, buildNumber = buildNumber, assets = assetBundle)
  }
}

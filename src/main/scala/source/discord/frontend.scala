package zone.slice.chaos
package source
package discord

import zone.slice.chaos.discord._

import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.{Request, Uri}
import org.http4s.client.Client

import scala.util.matching._

/**
  * A [[Source]] for Discord frontend [[discord.Build builds]].
  *
  * @param scraper the scraper
  */
case class FrontendSource[F[_]](
    val variant: Branch,
    val httpClient: Client[F],
)(implicit
    F: Sync[F],
) extends Source[F, FrontendBuild] {
  import FrontendSource._

  type V = Branch

  protected implicit def unsafeLogger: Logger[F] =
    Slf4jLogger.getLogger[F]

  /** Downloads the content of a Uri as a string. */
  protected def fetch(uri: Uri): F[String] = {
    val request = Request[F](uri = uri, headers = Headers.headers)
    Logger[F].debug(s"GET $uri") *> httpClient.expect[String](request)
  }

  /** Downloads the main client HTML of a [[discord.Branch]]. */
  def fetchClient(branch: Branch): F[String] =
    fetch(branch.uri / "channels" / "@me")

  /** Extracts assets from the HTML of `/channels/@me`. */
  def extractAssets(pageHtml: String): F[AssetBundle] = {
    def pull[A <: Asset](
        creator: String => A,
        notFoundException: Exception,
        regex: Regex,
    ): F[Vector[A]] = {
      val hashes = regex.findAllMatchIn(pageHtml).map(_.group(1))
      if (hashes.isEmpty) F.raiseError(notFoundException)
      else F.pure(hashes.map(creator).toVector)
    }

    import FrontendSourceError._
    (
      pull(asset => Asset.Script(asset), NoScripts, scriptTagRegex),
      pull(asset => Asset.Stylesheet(asset), NoStylesheets, styleTagRegex),
    ).mapN(AssetBundle.apply _)
  }

  /**
    * Fetches and extracts the build number and build hash from an
    * [[discord.AssetBundle]].
    */
  def fetchBuildInfo(assets: AssetBundle): F[(Int, String)] = {
    import FrontendSourceError._
    for {
      mainScript <- F.fromOption(assets.scripts.lastOption, NoScripts)
      text       <- fetch(mainScript.uri)
      maybeInfo =
        buildMetadataRegex
          .findFirstMatchIn(text)
          .map(match_ => (match_.group(1).toInt, match_.group(2)))
      info <- F.fromOption(maybeInfo, NoBuildInfo)
    } yield info
  }

  /**
    * Scrapes a [[discord.Branch]] for [[discord.FrontendBuild build information]].
    *
    * This takes care of downloading the branch's HTML, finding
    * [[discord.Asset]]s, extracting the build number, etc.
    */
  def build[A >: FrontendBuild]: F[A] = {
    for {
      pageText    <- fetchClient(variant)
      assetBundle <- extractAssets(pageText)
      info        <- fetchBuildInfo(assetBundle)
    } yield FrontendBuild(
      branch = variant,
      hash = info._2,
      number = info._1,
      assets = assetBundle,
    )
  }
}

object FrontendSource {

  /** The regex for matching script tags. */
  lazy val scriptTagRegex: UnanchoredRegex =
    """<script src="/assets/([.a-f0-9]+)\.js" integrity="[^"]+"></script>""".r.unanchored

  /** The regex for matching style tags. */
  lazy val styleTagRegex: UnanchoredRegex =
    """<link rel="stylesheet" href="/assets/([.a-f0-9]+)\.css" integrity="[^"]+">""".r.unanchored

  /** The regex for matching the build number and version hash. */
  lazy val buildMetadataRegex: UnanchoredRegex =
    """Build Number: (\d+), Version Hash: ([a-f0-9]+)""".r.unanchored
}

package zone.slice.chaos
package discord

import publish.Publish
import stream.FallibleStream

import fs2.Stream
import cats.syntax.all._
import cats.effect.Concurrent
import cats.effect.Temporal
import cats.effect.std.Random

import scala.util.matching._
import scala.concurrent.duration._

object FeRegexes {

  /** The regex for matching script tags. */
  lazy val scriptTag: UnanchoredRegex =
    """<script src="/assets/([.a-f0-9]+)\.js" integrity="[^"]+"></script>""".r.unanchored

  /** The regex for matching style tags. */
  lazy val styleTag: UnanchoredRegex =
    """<link rel="stylesheet" href="/assets/([.a-f0-9]+)\.css" integrity="[^"]+">""".r.unanchored

  /** The regex for matching the build number and version hash. */
  lazy val buildMetadata: UnanchoredRegex =
    """Build Number: (\d+), Version Hash: ([a-f0-9]+)""".r.unanchored
}

object FeBuilds {
  def apply[F[_]: Publish: Concurrent](
    branch: Branch,
  ): FallibleStream[F, FeBuild] =
    Stream.repeatEval(scrape(branch).attempt)

  def scrape[F[_]](
    branch: Branch,
  )(implicit F: Concurrent[F], P: Publish[F]): F[FeBuild] =
    for {
      _              <- P.output(s"Requesting $branch frontend")
      clientPageText <- P.get[String](branch.clientUri)
      scripts <- Asset
        .discover(FeRegexes.scriptTag, clientPageText, kind = AssetKind.Script)
        .pure[F]
      stylesheets <- Asset
        .discover(
          FeRegexes.styleTag,
          clientPageText,
          kind = AssetKind.Stylesheet,
        )
        .pure[F]
      entrypointScript <- scripts
        .get(3)
        .liftTo[F](new RuntimeException("entrypoint script not found"))
      _ <- P.output(
        s"Fetching entrypoint script: ${entrypointScript.uri}",
      )
      entrypointText <- P.get[String](entrypointScript.uri)
      buildMetadataMatch <- FeRegexes.buildMetadata
        .findFirstMatchIn(entrypointText)
        .liftTo[F](new RuntimeException("couldn't match build metadata"))
      buildNumber <- buildMetadataMatch
        .group(1)
        .toIntOption
        // should theoretically be impossible because of the regex
        .liftTo[F](new RuntimeException("build number wasn't a number?"))
      buildHash <- buildMetadataMatch.group(2).pure[F]
    } yield FeBuild(
      branch = branch,
      hash = buildHash,
      number = buildNumber,
      assets = AssetBundle(
        Map(
          AssetKind.Script     -> scripts,
          AssetKind.Stylesheet -> stylesheets,
        ),
      ),
    )

  private def fakeBuild(version: Int, branch: Branch): FeBuild =
    FeBuild(
      branch = branch,
      hash = "???",
      number = version,
      assets = AssetBundle.empty,
    )

  def fake[F[_]: Temporal](
    branch: Branch,
  )(implicit R: Random[F]): FallibleStream[F, FeBuild] = (for {
    baseVersion <- Stream.eval(R.betweenInt(10000, 100001))
    version     <- Stream.iterate(baseVersion)(_ + 1)
    repeats     <- Stream.eval(R.betweenInt(2, 7))
    version     <- Stream(version).repeatN(repeats.toLong)
  } yield fakeBuild(version, branch)).metered(1.second).map(_.asRight)
}

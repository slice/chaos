package zone.slice.chaos
package discord

import cats.{Show, Eq}
import cats.implicits._

/** A Discord client build for a specific [[Branch]].
  *
  * Contains build metadata such as the "build number" and all of the
  * surface-level assets of the build.
  */
final case class Build(
    branch: Branch,
    hash: String,
    number: Int,
    assets: AssetBundle,
)

object Build {
  implicit val showBuild: Show[Build] = (build: Build) =>
    show"Build(${build.branch}, ${build.number}, assets = ${build.assets})"

  implicit val eqBuild: Eq[Build] = (l: Build, r: Build) =>
    l.number == r.number && l.branch == r.branch
}

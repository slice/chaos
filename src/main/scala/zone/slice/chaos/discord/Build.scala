package zone.slice.chaos
package discord

import cats.Show
import cats.implicits._

/** A Discord client build for a specific [[Branch]].
  *
  * Contains build metadata such as the "build number" and all of the
  * surface-level assets of the build.
  */
final case class Build(branch: Branch, buildNumber: Int, assets: AssetBundle)

object Build {
  implicit val showBuild: Show[Build] = (build: Build) =>
    show"Build(${build.branch}, ${build.buildNumber}, assets = ${build.assets})"
}

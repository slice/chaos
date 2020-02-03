package zone.slice.chaos
package discord

import cats.Show
import cats.implicits._

/** A Discord client build. */
final case class Build(branch: Branch, buildNumber: Int, assets: Vector[Asset])

object Build {
  implicit val showBuild: Show[Build] = (build: Build) =>
    show"Build(${build.branch}, ${build.buildNumber}, assets = ${build.assets})"
}

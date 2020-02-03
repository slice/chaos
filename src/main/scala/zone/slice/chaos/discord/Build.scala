package zone.slice.chaos
package discord

import cats.Show

/** A Discord client build. */
final case class Build(branch: Branch, buildNumber: Int, assets: Vector[Asset])

object Build {
  implicit val showBuild: Show[Build] = (build: Build) =>
    s"Build(branch = ${build.branch}, buildNumber = ${build.buildNumber})"
}

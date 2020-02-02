package zone.slice.chaos
package discord

import cats.Show

case class Build(branch: Branch, buildNumber: Int)

object Build {
  implicit val showBuild: Show[Build] = (build: Build) =>
    s"Build(branch = ${build.branch}, buildNumber = ${build.buildNumber})"
}

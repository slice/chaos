package zone.slice.chaos
package discord

import cats.Show

/** A deploy of a [[Build]].
  *
  * This differs from a build object in that it describes its relation to other
  * builds -- something that wouldn't be appropriate in an individual build
  * object. For example, a deploy object is able to describe whether it is a
  * previous build that has been deployed again (a "revert").
  *
  * @param build
  *   the build
  * @param isRevert
  *   whether this build has been deployed before
  */
case class Deploy(
    build: Build,
    isRevert: Boolean,
)

object Deploy {
  implicit val showDeploy: Show[Deploy] = Show.fromToString[Deploy]
}

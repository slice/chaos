package zone.slice.chaos
package discord

import cats.Eq

case class FeBuild(
  branch: Branch,
  hash: String,
  number: Int,
  assets: AssetBundle,
)

object FeBuild {
  implicit val eqBuild: Eq[FeBuild] = Eq.by(_.number)
}

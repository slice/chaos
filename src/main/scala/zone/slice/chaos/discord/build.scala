package zone.slice.chaos.discord

import cats.Eq

case class FeBuild(
    branch: Branch,
    hash: String,
    number: Int,
    assets: AssetBundle,
)

given Eq[FeBuild] = Eq.by(_.number)

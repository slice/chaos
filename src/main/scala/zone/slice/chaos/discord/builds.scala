package zone.slice.chaos
package discord

import fs2.Stream
import cats.effect.Temporal
import cats.effect.std.Random

import scala.concurrent.duration._

object FeBuilds {
  def apply[F[_]](branch: Branch): Stream[F, FeBuild] =
    ???

  private def fakeBuild(version: Int, branch: Branch): FeBuild =
    FeBuild(
      branch = branch,
      hash = "???",
      number = version,
      assets = AssetBundle.empty,
    )

  def fake[F[_]: Temporal](
    branch: Branch,
  )(implicit R: Random[F]): Stream[F, FeBuild] = (for {
    baseVersion <- Stream.eval(R.betweenInt(10_000, 100_000 + 1))
    version     <- Stream.iterate(baseVersion)(_ + 1)
    repeats     <- Stream.eval(R.betweenInt(2, 6 + 1))
    version     <- Stream(version).repeatN(repeats.toLong)
  } yield fakeBuild(version, branch)).metered(1.second)
}

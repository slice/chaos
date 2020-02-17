package zone.slice.chaos
package discord

import Asset._

import cats.Show

/** A bundle of [[Asset.Script]]s and [[Asset.Stylesheet]]s for a [[Build]]. */
case class AssetBundle(
    scripts: Vector[Script],
    stylesheets: Vector[Stylesheet],
) {

  /** A vector of all [[Asset]]s contained within this [[AssetBundle]]. */
  def all: Vector[Asset] = scripts ++ stylesheets
}

object AssetBundle {
  implicit val showAssetBundle: Show[AssetBundle] =
    Show.fromToString[AssetBundle]
}

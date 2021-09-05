package zone.slice.chaos
package discord

case class Asset(kind: AssetKind, name: String)

sealed trait AssetKind {
  import AssetKind._

  def extension: String =
    this match {
      case Script     => "js"
      case Stylesheet => "css"
    }
}

object AssetKind {
  case object Script     extends AssetKind
  case object Stylesheet extends AssetKind
}

case class AssetBundle(assetMap: Map[AssetKind, Vector[Asset]]) {
  def assets: Vector[Asset] =
    assetMap.values.toVector.flatten
  def assetsOfKind(kind: AssetKind): Vector[Asset] =
    assetMap.get(kind).getOrElse(Vector.empty)
}

object AssetBundle {
  def empty: AssetBundle = AssetBundle(Map.empty)
}

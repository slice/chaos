package zone.slice.chaos.discord

case class Asset(kind: AssetKind, name: String)

enum AssetKind:
  case Script
  case Stylesheet

  def extension: String =
    this match
      case Script     => "js"
      case Stylesheet => "css"

case class AssetBundle(assetMap: Map[AssetKind, Vector[Asset]]):
  def assets: Vector[Asset] =
    assetMap.values.toVector.flatten
  def assetsOfKind(kind: AssetKind): Vector[Asset] =
    assetMap.get(kind).getOrElse(Vector.empty)

object AssetBundle:
  def empty: AssetBundle = AssetBundle(Map.empty)

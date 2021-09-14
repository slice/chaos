package zone.slice.chaos
package discord

import org.http4s.Uri

import scala.util.matching._

case class Asset(kind: AssetKind, name: String) {
  def uri: Uri =
    Uri.unsafeFromString(s"https://discord.com/assets/$name.${kind.extension}")
}

object Asset {
  def discover(
    regex: UnanchoredRegex,
    text: String,
    kind: AssetKind,
  ): Vector[Asset] =
    regex
      .findAllMatchIn(text)
      .map { m => Asset(kind = kind, name = m.group(1)) }
      .toVector
}

sealed abstract class AssetKind extends Product with Serializable {
  import AssetKind._

  def extension: String =
    this match {
      case Script     => "js"
      case Stylesheet => "css"
    }
}

object AssetKind {
  final case object Script     extends AssetKind
  final case object Stylesheet extends AssetKind
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

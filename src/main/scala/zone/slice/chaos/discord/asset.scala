package zone.slice.chaos
package discord

import Asset._

import cats.Show
import cats.implicits._
import org.http4s.Uri
import org.http4s.implicits._

/** A Discord client build asset (either an [[Asset.Script]] or [[Asset.Stylesheet]]). */
sealed trait Asset {

  /** The "name" of the asset. Usually a hash, but not strictly. */
  def name: String

  /** The extension of the asset. Usually either `"js"` or `"css"`. */
  def extension: String

  /** The filename of the asset as a Uri. Does not include the authority (`discordapp.com`).  */
  def filename: Uri = Uri.unsafeFromString(s"$name.$extension")

  /** The http4s Uri to this asset. */
  def uri: Uri = uri"https://discordapp.com/assets" / filename.path
}

object Asset {
  final case class Script(name: String) extends Asset {
    override val extension: String = "js"
  }

  final case class Stylesheet(name: String) extends Asset {
    override val extension: String = "css"
  }

  implicit val showBuild: Show[Asset] = (asset: Asset) =>
    show"${asset.name}.${asset.extension}"
}

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

  /** An empty asset bundle. */
  def empty: AssetBundle = AssetBundle(Vector.empty, Vector.empty)
}
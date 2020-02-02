package zone.slice.chaos
package discord

import org.http4s.Uri

/** A Discord client build asset (either an [[Asset.Script]] or [[Asset.Stylesheet]]). */
sealed trait Asset {

  /** The "name" of the asset. Usually a hash, but not strictly. */
  def name: String

  /** The extension of the asset. Usually either `"js"` or `"css"`. */
  def extension: String

  /** The filename of the asset as a Uri. Does not include the authority (`discordapp.com`).  */
  def filename: Uri = Uri.unsafeFromString(s"$name.$extension")
}

object Asset {
  final case class Script(name: String) extends Asset {
    override val extension: String = "js"
  }

  final case class Stylesheet(name: String) extends Asset {
    override val extension: String = "css"
  }
}

package zone.slice.chaos
package discord

import cats.{Show, Eq}
import cats.implicits._
import org.http4s.Uri
import io.circe._

/** A snapshot of a deployed Discord build at a point in time. */
sealed trait Build {

  /** The branch of the deployed build. */
  def branch: Branch

  /** The version of the deployed build. Can possibly be purely numeric. */
  def version: String

  /** The numerical version of the deployed build. This exists so that you may
    * order builds chronologically. */
  def number: Int
}

object Build {
  implicit val showBuild: Show[Build] = (build: Build) =>
    show"Build(${build.branch}, ${build.version})"

  implicit val eqBuild: Eq[Build] = Eq.by(_.version)
}

/** A Discord client build for a specific [[Branch]].
  *
  * Contains build metadata such as the "build number" and all of the
  * surface-level assets of the build.
  */
final case class FrontendBuild(
    branch: Branch,
    hash: String,
    number: Int,
    assets: AssetBundle,
) extends Build {
  val version = number.toString
}

object FrontendBuild {
  implicit val showBuild: Show[FrontendBuild] = (build: FrontendBuild) =>
    show"Build(${build.branch}, ${build.number}, assets = ${build.assets})"
}

/** A Discord host build for a specific [[Platform]] and [[Branch]]. */
final case class HostBuild(
    branch: Branch,
    platform: Platform,
    version: String,
    pubDate: String,
    notes: Option[String],
) extends Build {

  def uri: Uri = {
    // Discord omits "Stable" from their filenames when on the Stable branch.
    val branchCapitalized = branch.toString match {
      case "Stable" => ""
      case normal   => normal
    }

    // On the Stable branch, the download subdomain is just `dl` instead of
    // `dl-stable`, and the Linux deb file is just `discord-$VERSION` instead
    // of `discord-stable-$VERSION`.
    val branchKebab = branch.subdomain.map("-" + _).getOrElse("")
    val subdomain   = show"dl$branchKebab"

    val filename = platform match {
      case Platform.Mac     => show"Discord$branchCapitalized.zip"
      case Platform.Windows => show"Discord${branchCapitalized}Setup.exe"
      case Platform.Linux =>
        show"discord$branchKebab-$version.deb"
    }

    Uri.unsafeFromString(
      show"https://$subdomain.discordapp.net" +
        show"/apps/${platform.identifier}/$version/$filename",
    )
  }

  /** Typically, host builds have a versioning scheme of 0.0.X, where X is some
    * number.
    *
    * If we fail to extract X, we instead return the `hashCode` of the version
    * string itself. This will result in revert heuristics not functioning
    * properly, however.
    */
  def number =
    version
      .split('.')
      .lift(2) // Lift Z in X.Y.Z
      .flatMap(_.toIntOption)
      .getOrElse(version.##)
}

object HostBuild {
  implicit val showHostBuild: Show[HostBuild] = (build: HostBuild) =>
    show"HostBuild(${build.branch}, ${build.version}, )"

  implicit val decodeHostBuild: Decoder[HostBuild] = Decoder.instance {
    cursor =>
      for {
        version <- cursor.get[String]("name")
        pubDate <- cursor.get[String]("pub_date")
        notes   <- cursor.get[Option[String]]("notes")
        notesNormalized = notes match {
          // Sometimes we get `"note": ""`. Act as if no note was provided at
          // all.
          case Some("") => None
          case normal   => normal
        }
      } yield HostBuild(
        // We _have_ to specify a branch and platform. Because the JSON doesn't
        // supply this data (it's present in the URI), we just fill in Stable
        // and Windows by default.
        //
        // Anything using this Decoder should overwrite the values with
        // appropriate ones.
        Branch.Stable,
        Platform.Windows,
        version,
        pubDate,
        notesNormalized,
      )
  }
}

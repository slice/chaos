package zone.slice.chaos
package source
package discord

import zone.slice.chaos.discord._

import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import org.http4s.{Request, Uri}
import org.http4s.circe.jsonOf
import org.http4s.client.Client

case class HostSource[F[+_]: Sync](
    val branch: Branch,
    val platform: Platform,
    val httpClient: Client[F],
) extends Source[F, HostBuild] {

  protected implicit def unsafeLogger: Logger[F] =
    Slf4jLogger.getLogger[F]

  type V = (Branch, Platform)
  def variant = (branch, platform)

  def updatesUri: Uri = {
    val base           = "https://discordapp.com/api/updates"
    val platformString = platform.identifier
    val branchString   = branch.subdomain.getOrElse("stable")

    Uri.unsafeFromString(s"$base/$branchString?platform=$platformString")
  }

  implicit val circeConfiguration: Configuration =
    Configuration.default.withDefaults.withSnakeCaseMemberNames

  implicit def hostBuildDecoder = jsonOf[F, HostBuild]

  def build: F[HostBuild] = {
    for {
      _ <- Logger[F].debug(show"Fetching host updates for $branch on $platform")
      request = Request[F](uri = updatesUri, headers = Headers.headers)
      build <- httpClient.expect[HostBuild](request)
    } yield build.copy(branch = branch, platform = platform)
  }
}

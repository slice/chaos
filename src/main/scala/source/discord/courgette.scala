package zone.slice.chaos
package source
package discord

import zone.slice.chaos.discord._

import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.{Request, Uri, Query}
import org.http4s.implicits._
import org.http4s.circe.jsonOf
import org.http4s.client.Client

case class CourgetteSource[F[_]: Sync](
    val branch: Branch,
    val platform: Platform,
    val arch: Arch,
    val httpClient: Client[F],
) extends Source[F, CourgetteBuild] {
  protected implicit def unsafeLogger: Logger[F] =
    Slf4jLogger.getLogger[F]

  type V = (Branch, Platform, Arch)
  def variant = (branch, platform, arch)

  def manifestUri: Uri = {
    val base =
      uri"https://discord.com/api/updates/distributions/app/manifests/latest"
    base.copy(query =
      Query(
        "channel"  -> branch.show.toLowerCase.some,
        "platform" -> platform.identifier.some,
        "arch"     -> arch.show.toLowerCase.some,
      ),
    )
  }

  def build[A >: CourgetteBuild]: F[A] =
    for {
      _ <- Logger[F].debug(
        show"Fetching Courgette manifest (branch: $branch, platform: $platform, arch: $arch)",
      )
      request = Request[F](uri = manifestUri, headers = Headers.headers)
      entityDecoder =
        jsonOf(Sync[F], CourgetteBuild.decoder(branch, platform, arch))
      build <- httpClient.expect[CourgetteBuild](request)(entityDecoder)
    } yield build
}

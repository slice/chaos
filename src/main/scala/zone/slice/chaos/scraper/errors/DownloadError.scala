package zone.slice.chaos
package scraper.errors

import cats.Show
import org.http4s.{DecodeFailure, Response}

/** An error from the downloader. */
sealed trait DownloadError extends Exception
final case class DecodeError(error: DecodeFailure) extends DownloadError {
  override def getMessage: String = error.getMessage
}
final case class NetworkError(error: Throwable) extends DownloadError {
  override def getMessage: String = error.getMessage
}
final case class HTTPError[F[_]](response: Response[F]) extends DownloadError {
  override val getMessage: String =
    s"Server returned ${response.status.code} ${response.status.reason}"
}
object DownloadError {
  implicit val showDownloadError: Show[DownloadError] =
    Show.fromToString[DownloadError]
}

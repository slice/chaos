package zone.slice.chaos
package scraper.errors

import cats.Show
import org.http4s.{MessageFailure, Status}

/** An error from the downloader. */
sealed trait DownloadError extends Exception
final case class DecodeError(error: MessageFailure) extends DownloadError {
  override def getMessage: String = error.getMessage
}
final case class NetworkError(error: Throwable) extends DownloadError {
  override def getMessage: String = error.getMessage
}
final case class HTTPError(status: Status) extends DownloadError {
  override val getMessage: String =
    s"Server returned ${status.code} ${status.reason}"
}
object DownloadError {
  implicit val showDownloadError: Show[DownloadError] =
    Show.fromToString[DownloadError]
}

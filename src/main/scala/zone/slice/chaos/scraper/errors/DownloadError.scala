package zone.slice.chaos
package scraper.errors

import cats.Show
import org.http4s.{MessageFailure, Status}

/**
  * An error from the downloader. Possible failure scenarios include failing
  * to decode the response body, some network error, etc.
  */
sealed trait DownloadError extends Exception

object DownloadError {

  /** Thrown when something went wrong with decoding the HTTP body. */
  final case class DecodeError(error: MessageFailure) extends DownloadError {
    override def getMessage: String = error.getMessage
  }

  /** Thrown when something went wrong with the network somewhere. */
  final case class NetworkError(error: Throwable) extends DownloadError {
    override def getMessage: String = error.getMessage
  }

  /** Thrown when a failing (non-2xx) HTTP response was encountered. */
  final case class HTTPError(status: Status) extends DownloadError {
    override val getMessage: String =
      s"Server returned ${status.code} ${status.reason}"
  }

  implicit val showDownloadError: Show[DownloadError] =
    Show.fromToString[DownloadError]
}

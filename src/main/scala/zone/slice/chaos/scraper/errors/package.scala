package zone.slice.chaos
package scraper

import org.http4s.{DecodeFailure, Response}

package object errors {

  /** An error from the downloader. */
  sealed trait DownloadError extends Exception
  final case class DecodeError(error: DecodeFailure) extends DownloadError
  final case class NetworkError(error: Throwable) extends DownloadError
  final case class HTTPError[F[_]](response: Response[F]) extends DownloadError

  /** An error from the extractor. */
  sealed trait ExtractorError extends Exception
  case object NoScripts extends ExtractorError
  case object NoStylesheets extends ExtractorError
  case object NoBuildNumber extends ExtractorError

  sealed trait ScraperError
  object ScraperError {
    final case class Download(error: DownloadError) extends ScraperError
    final case class Extractor(error: ExtractorError) extends ScraperError
  }
}

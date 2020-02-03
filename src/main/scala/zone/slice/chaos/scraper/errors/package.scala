package zone.slice.chaos
package scraper

import cats.Show
import org.http4s.{DecodeFailure, Response}

package object errors {

  /** An error from the downloader. */
  sealed trait DownloadError extends Exception
  final case class DecodeError(error: DecodeFailure) extends DownloadError {
    override def getMessage: String = error.getMessage
  }
  final case class NetworkError(error: Throwable) extends DownloadError {
    override def getMessage: String = error.getMessage
  }
  final case class HTTPError[F[_]](response: Response[F])
      extends DownloadError {
    override val getMessage: String =
      s"Server returned ${response.status.code} ${response.status.reason}"
  }
  object DownloadError {
    implicit val showDownloadError: Show[DownloadError] =
      Show.fromToString[DownloadError]
  }

  /** An error from the extractor. */
  sealed trait ExtractorError extends Exception
  case object NoScripts extends ExtractorError {
    override val getMessage: String = "No script tags were found"
  }
  case object NoStylesheets extends ExtractorError {
    override val getMessage: String = "No stylesheet tags were found"
  }
  case object NoBuildNumber extends ExtractorError {
    override val getMessage: String = "A build number couldn't be found"
  }
  object ExtractorError {
    implicit val showExtractorError: Show[ExtractorError] =
      Show.fromToString[ExtractorError]
  }

  sealed trait ScraperError extends Exception
  object ScraperError {
    final case class Download(error: DownloadError) extends ScraperError {
      override def getMessage: String = s"Downloader error: ${error.getMessage}"
    }

    final case class Extractor(error: ExtractorError) extends ScraperError {
      override def getMessage: String = s"Extractor error: ${error.getMessage}"
    }

    implicit val showScraperError: Show[ScraperError] =
      Show.fromToString[ScraperError]
  }
}

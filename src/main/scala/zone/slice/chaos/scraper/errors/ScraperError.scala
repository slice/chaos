package zone.slice.chaos
package scraper.errors

import cats.Show

import scala.util.control.NoStackTrace

/**
  * An error from the scraper, which handles both downloading and extracting.
  */
sealed trait ScraperError extends Exception

object ScraperError {

  /** A [[DownloadError]]. */
  final case class Download(error: DownloadError)
      extends ScraperError
      with NoStackTrace {
    override def getMessage: String = s"Downloader error: ${error.getMessage}"
  }

  /** An [[ExtractorError]]. */
  final case class Extractor(error: ExtractorError)
      extends ScraperError
      with NoStackTrace {
    override def getMessage: String = s"Extractor error: ${error.getMessage}"
  }

  implicit val showScraperError: Show[ScraperError] =
    Show.fromToString[ScraperError]
}

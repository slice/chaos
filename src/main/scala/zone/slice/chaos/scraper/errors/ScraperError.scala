package zone.slice.chaos
package scraper.errors

import cats.Show

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

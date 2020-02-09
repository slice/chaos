package zone.slice.chaos
package scraper.errors

import cats.Show

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

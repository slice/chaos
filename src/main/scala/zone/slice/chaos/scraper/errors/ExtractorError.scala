package zone.slice.chaos
package scraper.errors

import cats.Show

import scala.util.control.NoStackTrace

/**
  * An error from the extractor. Failure scenarios usually arise when certain
  * entities cannot be found in the content of an asset.
  */
sealed trait ExtractorError extends Exception

object ExtractorError {

  /** Thrown when no script tags were found. */
  case object NoScripts extends ExtractorError with NoStackTrace {
    override val getMessage: String = "No script tags were found"
  }

  /** Thrown when no stylesheet tags were found. */
  case object NoStylesheets extends ExtractorError with NoStackTrace {
    override val getMessage: String = "No stylesheet tags were found"
  }

  /** Thrown when no build number could be found. */
  case object NoBuildNumber extends ExtractorError with NoStackTrace {
    override val getMessage: String = "A build number couldn't be found"
  }

  implicit val showExtractorError: Show[ExtractorError] =
    Show.fromToString[ExtractorError]
}

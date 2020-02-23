package zone.slice.chaos
package source
package discord

import cats.Show

import scala.util.control.NoStackTrace

/**
  * An error from the extractor. Failure scenarios usually arise when certain
  * entities cannot be found in the content of an asset.
  */
sealed trait FrontendSourceError extends Exception

object FrontendSourceError {

  /** Thrown when no script tags were found. */
  case object NoScripts extends FrontendSourceError with NoStackTrace {
    override val getMessage: String = "No script tags were found"
  }

  /** Thrown when no stylesheet tags were found. */
  case object NoStylesheets extends FrontendSourceError with NoStackTrace {
    override val getMessage: String = "No stylesheet tags were found"
  }

  /** Thrown when no build number or version hash could be found. */
  case object NoBuildInfo extends FrontendSourceError with NoStackTrace {
    override val getMessage: String =
      "A build number or version hash couldn't be found"
  }

  implicit val showFrontendSourceError: Show[FrontendSourceError] =
    Show.fromToString[FrontendSourceError]
}

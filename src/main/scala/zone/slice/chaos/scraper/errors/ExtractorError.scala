package zone.slice.chaos

package scraper
package errors

sealed trait ExtractorError extends Exception
case object NoResources extends ExtractorError

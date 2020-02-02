package zone.slice.chaos

package scraper
package errors

import org.http4s.{DecodeFailure, Response}

sealed trait DownloadError extends Exception
final case class DecodeError(error: DecodeFailure) extends DownloadError
final case class NetworkError(error: Throwable) extends DownloadError
final case class HTTPError[F[_]](response: Response[F]) extends DownloadError

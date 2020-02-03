package zone.slice.chaos
package scraper

import errors._

import cats.data.EitherT
import cats.implicits._
import cats.effect._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.Client
import org.http4s.headers._
import org.http4s.Method._
import org.http4s.Status.Successful
import org.http4s.Uri

/**
  * A [[Scraper]] which uses an http4s Client to perform HTTP requests.
  */
class Http4sScraper[F[_]: Sync](client: Client[F])
    extends Scraper[F]
    with Http4sClientDsl[F] {
  private implicit def unsafeLogger[F[_]: Sync]: Logger[F] =
    Slf4jLogger.getLogger[F]

  private def products: (AgentProduct, List[AgentToken]) = {
    val mainProduct = AgentProduct(BuildInfo.name, BuildInfo.version.some)
    val otherTokens = List(
      AgentProduct("scala", BuildInfo.scalaVersion.some),
      AgentProduct("http4s", org.http4s.BuildInfo.version.some)
    )

    (mainProduct, BuildInfo.homepage match {
      case None           => otherTokens
      case Some(homepage) => AgentComment(homepage.toString) +: otherTokens
    })
  }

  override def fetch(uri: Uri): EitherT[F, DownloadError, String] = {
    val (mainProduct, otherTokens) = products
    val headers = List(`User-Agent`(mainProduct, otherTokens))
    val request = GET(uri, headers: _*)

    val result = client.fetch[Either[DownloadError, String]](request) {
      case Successful(response) =>
        response
          .attemptAs[String]
          .leftMap[DownloadError](DecodeError)
          .value
      case failedResponse => Sync[F].pure(Left(HTTPError(failedResponse)))
    }

    EitherT(for {
      _ <- Logger[F].info(s"GET $uri")
      handled <- result.handleError(error => Left(NetworkError(error)))
    } yield handled)
  }
}

package zone.slice.chaos
package scraper

import errors._
import discord._
import cats.data.EitherT
import cats.implicits._
import cats.effect._
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
  private def products: (AgentProduct, List[AgentToken]) = {
    val mainProduct = AgentProduct(BuildInfo.name, BuildInfo.version.some)
    val otherTokens = List(AgentProduct("scala", BuildInfo.scalaVersion.some))

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
          .leftMap(DecodeError)
          .leftWiden[DownloadError]
          .value
      case failedResponse => Sync[F].pure(Left(HTTPError(failedResponse)))
    }

    EitherT(result.handleErrorWith { error =>
      Sync[F].pure(Left(NetworkError(error)))
    })
  }
}

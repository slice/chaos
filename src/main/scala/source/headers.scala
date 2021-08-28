package zone.slice.chaos
package source

import org.http4s.{Headers => Http4sHeaders, ProductId, ProductComment}
import org.http4s.headers.`User-Agent`

object Headers {
  def userAgentHeader: `User-Agent` = {
    val mainProduct = ProductId(BuildInfo.name, Some(BuildInfo.version))
    val otherTokens = List(
      ProductId("scala", Some(BuildInfo.scalaVersion)),
      ProductId("http4s", Some(org.http4s.BuildInfo.version)),
    )

    `User-Agent`(
      mainProduct,
      BuildInfo.homepage match {
        case None           => otherTokens
        case Some(homepage) => ProductComment(homepage.toString) +: otherTokens
      },
    )
  }

  lazy val headers: Http4sHeaders = Http4sHeaders(userAgentHeader)
}

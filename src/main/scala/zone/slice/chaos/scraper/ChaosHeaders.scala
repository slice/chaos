package zone.slice.chaos.scraper

import org.http4s.Headers
import org.http4s.headers.{AgentComment, AgentProduct, `User-Agent`}
import zone.slice.chaos.BuildInfo

object ChaosHeaders {
  def userAgentHeader: `User-Agent` = {
    val mainProduct = AgentProduct(BuildInfo.name, Some(BuildInfo.version))
    val otherTokens = List(
      AgentProduct("scala", Some(BuildInfo.scalaVersion)),
      AgentProduct("http4s", Some(org.http4s.BuildInfo.version))
    )

    `User-Agent`(mainProduct, BuildInfo.homepage match {
      case None           => otherTokens
      case Some(homepage) => AgentComment(homepage.toString) +: otherTokens
    })
  }

  lazy val headers: Headers = Headers.of(userAgentHeader)
}

package zone.slice.chaos
package source

import org.http4s.{Headers => Http4sHeaders}
import org.http4s.headers.{`User-Agent`, AgentComment, AgentProduct}

object Headers {
  def userAgentHeader: `User-Agent` = {
    val mainProduct = AgentProduct(BuildInfo.name, Some(BuildInfo.version))
    val otherTokens = List(
      AgentProduct("scala", Some(BuildInfo.scalaVersion)),
      AgentProduct("http4s", Some(org.http4s.BuildInfo.version)),
    )

    `User-Agent`(mainProduct, BuildInfo.homepage match {
      case None           => otherTokens
      case Some(homepage) => AgentComment(homepage.toString) +: otherTokens
    })
  }

  lazy val headers: Http4sHeaders = Http4sHeaders.of(userAgentHeader)
}

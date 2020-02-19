package zone.slice.chaos

import cats.effect.IO
import org.http4s.implicits._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.HttpRoutes

trait OkHttpClientFixture {

  val http = HttpRoutes
    .of[IO] {
      case _ => Ok()
    }
    .orNotFound
  implicit val httpClient = Client.fromHttpApp[IO](http)
}

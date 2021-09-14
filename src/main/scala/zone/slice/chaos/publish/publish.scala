package zone.slice.chaos
package publish

import cats.Apply
import cats.syntax.all._
import cats.effect.std.Console
import org.http4s.{Uri, Method}
import org.http4s.client.Client
import org.http4s.{Request, EntityEncoder, EntityDecoder}

/** Publishing operations that publishers can perform. */
trait Publish[F[_]] {
  def output(text: String): F[Unit]

  def request[A](request: Request[F])(implicit ev: EntityDecoder[F, A]): F[A]

  def get[A](from: Uri)(implicit ev: EntityDecoder[F, A]): F[A] =
    this.request(Request[F](uri = from))

  def post[I, O](to: Uri, body: I)(implicit
    decoder: EntityDecoder[F, O],
    encoder: EntityEncoder[F, I],
  ): F[O] =
    this.request(Request[F](method = Method.POST, uri = to).withEntity(body))
}

object Publish {
  def apply[F[_]](implicit ev: Publish[F]): Publish[F] = ev

  def make[F[_]: Apply](console: Console[F], client: Client[F]): Publish[F] =
    new Publish[F] {
      def output(text: String): F[Unit] =
        console.errorln(text)

      def request[A](request: Request[F])(implicit
        decoder: EntityDecoder[F, A],
      ): F[A] = {
        val log = s"HTTP ${request.method.name} ${request.uri}"
        console.errorln(log) *> client.expect(request)
      }
    }
}

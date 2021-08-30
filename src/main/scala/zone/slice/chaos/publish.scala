package zone.slice.chaos

import fs2.Stream
import fs2.concurrent.Topic
import cats.{Applicative, Functor}
import cats.syntax.all.*
import cats.effect.std.Console
import org.http4s.{Uri, Method}
import org.http4s.client.Client
import org.http4s.{Request, EntityEncoder, EntityDecoder, EntityBody}

/** Publishing operations that publishers can perform. */
trait Publish[F[_]]:
  def output(text: String): F[Unit]

  def request[A](request: Request[F])(using EntityDecoder[F, A]): F[A]

  def get[A](from: Uri)(using EntityDecoder[F, A]): F[A] =
    this.request(Request[F](uri = from))
  def post[I, O](to: Uri, body: I)(using
      EntityDecoder[F, O],
      EntityEncoder[F, I],
  ): F[O] =
    this.request(Request[F](method = Method.POST, uri = to).withEntity(body))

object Publish:
  def make[F[_]](console: Console[F], client: Client[F]): Publish[F] =
    new Publish[F] {
      def output(text: String): F[Unit] =
        console.errorln(text)
      def request[A](request: Request[F])(using EntityDecoder[F, A]): F[A] =
        client.expect(request)
    }

/** A function that publishes something. */
type Publisher[F[_], -A] = (A, Publish[F]) => F[Unit]

extension [F[_], A](publisher: Publisher[F, A])(using F: Applicative[F])
  def when(cond: A => Boolean): Publisher[F, A] =
    (a, p) => publisher(a, p).whenA(cond(a))

/** Forward things from a topic into a publisher. */
def subscribe[F[_]: Functor, A](topic: Topic[F, A], f: Publisher[F, A])(using
    publish: Publish[F],
): Stream[F, Nothing] =
  topic
    .subscribe(0)
    .evalTap(f(_, publish))
    .drain

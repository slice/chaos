package zone.slice.chaos

import fs2.Stream
import fs2.concurrent.Topic
import cats.{Applicative, Functor}
import cats.syntax.all.*

/** Publishing operations that publishers can perform. */
trait Publish[F[_]]:
  def output(text: String): F[Unit]

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

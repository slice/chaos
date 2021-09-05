package zone.slice.chaos

import fs2.Stream
import fs2.concurrent.Topic
import cats.{Applicative, Functor}
import cats.syntax.all._

package object publish {

  /** A function that publishes something. */
  type Publisher[F[_], -A] = (A, Publish[F]) => F[Unit]

  implicit class PublisherOps[F[_], A](publisher: Publisher[F, A]) {

    /** Conditionalizes a publisher. */
    def when(cond: A => Boolean)(implicit
        ev: Applicative[F],
    ): Publisher[F, A] =
      (a, p) => publisher(a, p).whenA(cond(a))
  }

  /** Forward things from a topic into a publisher. */
  def subscribe[F[_]: Functor, A](topic: Topic[F, A], f: Publisher[F, A])(
      implicit publish: Publish[F],
  ): Stream[F, Nothing] =
    topic
      .subscribe(0)
      .evalTap(f(_, publish))
      .drain
}


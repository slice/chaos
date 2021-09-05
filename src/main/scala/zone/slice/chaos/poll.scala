package zone.slice.chaos

import fs2.{Pipe, Pull}
import fs2.concurrent.Topic
import cats.Eq
import cats.effect.Concurrent

package object poll {

  /** Emit any changes in a stream, additionally publishing them to a topic. */
  def poll[F[_]: Concurrent, A: Eq](
    topic: Topic[F, A],
  ): Pipe[F, A, A] =
    _.changes.evalTap(topic.publish1)

  /** Filters only the first value of a stream. */
  def filter1[F[_], A](predicate: A => Boolean): Pipe[F, A, A] =
    (st) =>
      st.pull.uncons1.flatMap {
        case Some((head, tail)) =>
          if (predicate(head)) Pull.output1(head) >> tail.pull.echo
          else tail.pull.echo
        case None => Pull.done
      }.stream
}

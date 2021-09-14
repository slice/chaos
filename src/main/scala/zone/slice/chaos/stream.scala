package zone.slice.chaos

import discord._
import fs2.{Stream, Pipe, Pull}

package object stream {

  /** A stream that may emit either Throwables or values. */
  type FallibleStream[F[_], +A] = Stream[F, Either[Throwable, A]]

  /** A fallible stream that emits builds. */
  type BuildStream[F[_]] = FallibleStream[F, FeBuild]

  /** A labeled value. Typically used to track build streams. */
  type Labeled[A] = (String, A)

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

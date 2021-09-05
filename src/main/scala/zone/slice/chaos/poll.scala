package zone.slice.chaos

import fs2.{Pipe, Stream, Pull}
import fs2.concurrent.Topic
import cats.Eq
import cats.effect.Concurrent

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
        if predicate(head) then Pull.output1(head) >> tail.pull.echo
        else tail.pull.echo
      case None => Pull.done
    }.stream

/** Deduplicate the first values from a stream of labeled streams according to a
  * state.
  */
def dedup1FromState[F[_], A](state: State)(
    valueToVersion: A => Number,
): Pipe[F, (String, Stream[F, A]), (String, Stream[F, A])] =
  _.map { case label -> stream =>
    val deduplicationFilter: fs2.Pipe[F, A, A] =
      filter1(firstValue =>
        state
          .get(label)
          .map(_ != valueToVersion(firstValue))
          // if there's no last known latest version, always publish
          .getOrElse(true),
      )

    label -> stream.through(deduplicationFilter)
  }

package zone.slice.chaos

import fs2.Stream
import fs2.concurrent.Topic
import cats.Eq
import cats.effect.Concurrent

/** Emit any changes in a stream, additionally publishing them to a topic. */
def poll[F[_]: Concurrent, A: Eq](
    things: Stream[F, A],
    topic: Topic[F, A],
): Stream[F, A] =
  things.changes.evalTap(topic.publish1)

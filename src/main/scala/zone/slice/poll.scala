package zone.slice

import fs2.Stream
import fs2.concurrent.Topic
import cats.Eq
import cats.effect.Concurrent

/** Submit any changes in a stream to a topic. */
def poll[F[_]: Concurrent, A: Eq](
    things: Stream[F, A],
    topic: Topic[F, A],
): Stream[F, Nothing] =
  things.changes.through(topic.publish).drain

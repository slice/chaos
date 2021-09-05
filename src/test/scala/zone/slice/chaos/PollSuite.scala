package zone.slice.chaos

import cats.effect._
import fs2.Stream
import fs2.concurrent.Topic
import munit.CatsEffectSuite

import zone.slice.chaos.poll._
import zone.slice.chaos.State

class PollSuite extends CatsEffectSuite {
  test("poll works") {
    val baseStream = Stream(1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3)
    val changes    = baseStream.changes.toVector

    Topic[IO, Int].flatMap { topic =>
      topic.subscribeAwait(Int.MaxValue).use { subscriber =>
        baseStream
          .through(poll(topic))
          .compile
          .toVector
          .map(
            assertEquals(
              _,
              changes,
              "changed elements were not passed through",
            ),
          ) *> topic.close *> subscriber.compile.toVector.map(
          assertEquals(
            _,
            changes,
            "changed elements were not published to topic",
          ),
        )
      }
    }
  }

  test("filter1 only filters the first element") {
    assertEquals(
      Stream(36, 36, 36).through(filter1(_ != 36)).toVector,
      Vector(36, 36),
    )

    assertEquals(
      Stream(true, true, false).through(filter1(identity)).toVector,
      Vector(true, true, false),
    )
  }

  test("State.deduplicateFirst works") {
    val state = new State(Map("cool" -> 2))

    assertEquals(
      Stream(2, 2, 2, 3)
        .through(state.deduplicateFirst("cool")(n => n))
        .toVector,
      // the app itself actually wants something like Vector(3), but this
      // combinator doesn't handle that---we do .changes on the stream
      // beforehand.
      Vector(2, 2, 3),
      "first occurrence of last known version wasn't dropped",
    )

    assertEquals(
      Stream(1, 2, 3)
        .through(State.empty.deduplicateFirst("cool")(n => n))
        .toVector,
      Vector(1, 2, 3),
      "values with no last known version weren't published"
    )
  }
}

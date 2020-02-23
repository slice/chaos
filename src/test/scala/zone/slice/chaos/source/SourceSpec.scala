package zone.slice.chaos
package source

import source._
import cats.effect._
import cats.effect.laws.util.TestContext
import cats.implicits._

import fs2.Stream

import scala.concurrent.duration._

class SourceSpec extends ChaosSpec {
  trait SourceFixture {
    val source = new Source[IO, String] {
      type K = String

      def builds(kind: K): Stream[IO, String] = {
        val ints = Stream.iterate(0)(_ + 1)
        ints.map(version => s"$kind, $version")
      }
    }
  }

  trait SourcePollFixture extends SourceFixture {
    // Use a TestContext to simulate time.
    val ctx            = TestContext()
    implicit val timer = ctx.ioTimer

    // A mocked tapper function to use with #poll and #parPoll.
    val tapper = mock[PollResult[String] => IO[Unit]]
    // tapper(*) answers ((thing: String) => IO(println(s"[tapper] $thing")))
    tapper(*) returns IO.unit
  }

  "source" - {
    "emits builds" in new SourceFixture {
      val builds = source.builds("cat").take(5).compile.toVector.run()
      forAll(builds)(_ should fullyMatch regex "cat, \\d+")
    }

    "polls" in new SourcePollFixture {
      val cancel = source
        .poll("cat", 1.second)(tapper, _ => IO.unit)
        .compile
        .drain
        .unsafeRunCancelable(_ => ())

      // The initial pull should've been tapped by now.
      tapper(*) wasCalled once

      // After a second, the post-delay pull should've happened.
      ctx.tick(1.second)
      tapper(*) wasCalled twice

      // After another second, another post-delay pull should've happened.
      ctx.tick(1.second)
      tapper(*) wasCalled 3.times

      cancel.run()
    }

    "parallel polls" in new SourcePollFixture {
      val cancel = Stream("cat", "dog")
        .map(source.poll(_, 10.millis)(tapper, _ => IO.unit))
        .parJoinUnbounded
        .compile
        .drain
        .unsafeRunCancelable(_ => ())

      // Stream#parJoin takes a bit of time to start emitting stuff.
      Thread.sleep(50L)
      tapper(*) wasCalled twice

      ctx.tick(10.millis)
      // Ditto.
      Thread.sleep(50L)
      tapper(*) wasCalled 4.times

      cancel.run()
    }
  }
}

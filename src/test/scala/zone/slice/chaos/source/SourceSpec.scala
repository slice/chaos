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

    /**
      * Shortcut function to check if a `PollResult` was tapped.
      *
      * This really only works for unique, one-time taps since we check if the
      * tapper function was called once.
      */
    def wasTapped(build: String, previous: Option[String]): Unit =
      tapper(PollResult(build, previous)) wasCalled once
  }

  "source" - {
    "emits builds" in new SourceFixture {
      val builds = source.builds("cat").take(5).compile.toVector.unsafeRunSync()
      all(builds) should fullyMatch regex "cat, \\d+"
    }

    "polls" in new SourcePollFixture {
      val cancel = source
        .poll("cat", 1.second)(tapper, _ => IO.unit)
        .compile
        .drain
        .unsafeRunCancelable(_ => ())

      // The initial pull should've been tapped by now.
      wasTapped("cat, 0", none)

      // After a second, the post-delay pull should've happened.
      ctx.tick(1.second)
      wasTapped("cat, 1", "cat, 0".some)

      // After another second, another post-delay pull should've happened.
      ctx.tick(1.second)
      wasTapped("cat, 2", "cat, 1".some)

      cancel.unsafeRunSync()
    }

    "parallel polls" in new SourcePollFixture {
      val cancel = Stream("cat", "dog")
        .map(source.poll(_, 10.millis)(tapper, _ => IO.unit))
        .parJoinUnbounded
        .compile
        .drain
        .unsafeRunCancelable(_ => ())

      // Stream#parJoin takes a bit of time to start emitting stuff.
      Thread.sleep(500L)
      wasTapped("cat, 0", none)
      wasTapped("dog, 0", none)

      ctx.tick(10.millis)
      Thread.sleep(500L) // Ditto.
      // We can't check the actual arguments because the order isn't
      // deterministic.
      tapper(*) wasCalled 4.times

      cancel.unsafeRunSync()
    }

    "handles errors" in new SourcePollFixture {
      val error = new Exception("oops, dropped something")

      val failingSource = new Source[IO, String] {
        type K = String

        def builds(kind: String): Stream[IO, String] =
          Stream(kind) ++ Stream.raiseError[IO](error)
      }

      val errorHandler = mock[Throwable => IO[Unit]]
      errorHandler(*) returns IO.unit

      val cancel = failingSource
        .poll("cat", 10.millis)(tapper, errorHandler)
        .compile
        .drain
        .unsafeRunCancelable(_ => ())

      ctx.tick(10.millis)
      errorHandler(error) wasCalled once

      cancel.unsafeRunSync()
    }
  }
}

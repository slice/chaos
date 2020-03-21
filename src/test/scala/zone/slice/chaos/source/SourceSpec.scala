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
    case class FixedVariantSource(val variant: String) extends Source[IO, String] {
      type V = String
      def builds: Stream[IO, String] = {
        val ints = Stream.iterate(0)(_ + 1)
        ints.map(version => s"$variant, $version")
      }
    }
  }

  trait SourcePollFixture extends SourceFixture {
    // Use a TestContext to simulate time.
    val ctx            = TestContext()
    implicit val timer = ctx.ioTimer

    // A mocked tapper function to use with #poll and #parPoll.
    val tapper = mock[Either[Throwable, Poll[String]] => IO[Unit]]
    // tapper(*) answers ((thing: String) => IO(println(s"[tapper] $thing")))
    tapper(*) returns IO.unit

    /**
      * Shortcut function to check if a `Poll` was tapped.
      *
      * This really only works for unique, one-time taps since we check if the
      * tapper function was called once.
      */
    def wasTapped(build: String, previous: Option[String]): Unit =
      tapper(Right(Poll(build, previous))) wasCalled once
  }

  "source" - {
    "emits builds" in new SourceFixture {
      val builds = FixedVariantSource("cat").builds.take(3).compile.toVector.unsafeRunSync()
      builds shouldBe Vector("cat, 0", "cat, 1", "cat, 2")
    }

    "polls" in new SourcePollFixture {
      val cancel = FixedVariantSource("cat")
        .poll(1.second)
        .evalTap(tapper)
        .compile
        .drain
        .unsafeRunCancelable(_ => ())

      wasTapped("cat, 0", none)

      ctx.tick(1.second)
      wasTapped("cat, 1", "cat, 0".some)

      ctx.tick(1.second)
      wasTapped("cat, 2", "cat, 1".some)

      cancel.unsafeRunSync()
    }

    "polls with a initial" in new SourcePollFixture {
      val cancel = FixedVariantSource("cat")
        .poll(1.second, "initial cat".some)
        .evalTap(tapper)
        .compile
        .drain
        .unsafeRunCancelable(_ => ())

      wasTapped("cat, 0", "initial cat".some)

      ctx.tick(1.second)
      wasTapped("cat, 1", "cat, 0".some)

      cancel.unsafeRunSync()
    }

    "parallel polls" in new SourcePollFixture {
      val cancel = Stream("cat", "dog")
        .map(variant => FixedVariantSource(variant))
        .map(_.poll(10.millis))
        .parJoinUnbounded
        .evalTap(tapper)
        .compile
        .drain
        .unsafeRunCancelable(_ => ())

      // Stream#parJoin takes a bit of time to start emitting stuff.
      // A bit ugly, but parJoin is nondeterministic, unfortunately.
      Thread.sleep(500L)
      wasTapped("cat, 0", none)
      wasTapped("dog, 0", none)

      ctx.tick(10.millis)
      Thread.sleep(500L) // Ditto.

      // We can't check the actual arguments because of the nondeterminism.
      tapper(*) wasCalled 4.times

      cancel.unsafeRunSync()
    }

    "handles errors" in new SourcePollFixture {
      val error = new Exception("oops, dropped something")

      val failingSource = new Source[IO, String] {
        type V = String
        def variant: String = "cat"
        def builds: Stream[IO, String] =
          Stream(variant) ++ Stream.raiseError[IO](error)
      }

      val cancel = failingSource
        .poll(10.millis)
        .evalTap(tapper)
        .compile
        .drain
        .unsafeRunCancelable(_ => ())

      ctx.tick(10.millis)
      tapper(Left(error)) wasCalled once

      cancel.unsafeRunSync()
    }
  }
}

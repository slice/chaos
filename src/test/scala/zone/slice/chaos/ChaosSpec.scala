package zone.slice.chaos

import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.freespec.AnyFreeSpec
import cats.effect.IO
import cats.effect.Timer
import cats.effect.ContextShift

import scala.concurrent.ExecutionContext

abstract class ChaosSpec
    extends AnyFreeSpec
    with Matchers
    with IdiomaticMockito {
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  implicit class IOOps[A](val io: IO[A]) {

    /** Alias for unsafeRunSync. */
    def run(): A = io.unsafeRunSync()
  }
}

package zone.slice.chaos

import org.mockito.{IdiomaticMockito, ArgumentMatchersSugar}
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.freespec.AnyFreeSpec
import cats.effect.IO
import cats.effect.Timer
import cats.effect.ContextShift

import scala.concurrent.ExecutionContext

abstract class ChaosSpec
    extends AnyFreeSpec
    with Matchers
    with Inspectors
    with IdiomaticMockito
    with ArgumentMatchersSugar {
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)
}

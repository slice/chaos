package zone.slice.chaos

import cats.effect.{IO, SyncIO}
import munit.CatsEffectSuite

class HelloWorldSuite extends CatsEffectSuite {
  test("the world exists") {
    IO(1 + 1).map(it => assertEquals(it, 2))
  }
}

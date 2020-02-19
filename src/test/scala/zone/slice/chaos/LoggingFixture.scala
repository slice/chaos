package zone.slice.chaos

import cats.effect.IO
import io.chrisdavenport.log4cats.testing.TestingLogger

trait LoggingFixture {
  implicit val logger: TestingLogger[IO] = TestingLogger.impl[IO]()

  def logged: IO[Vector[TestingLogger.LogMessage]] =
    logger.logged
}

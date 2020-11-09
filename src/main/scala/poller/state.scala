package zone.slice.chaos
package poller

import cats.effect._
import cats.implicits._
import fs2.io.file
import fs2.text

import java.nio.file.Path

object State {
  type Store = Map[String, String]

  def decode(contents: String): Store =
    contents.linesIterator.collect {
      case s"$selector=$version" => (selector, version)
    }.toMap

  def encode(store: Store): String =
    store
      .map {
        case selector -> version => s"$selector=$version"
      }
      .mkString("\n")

  def read[F[_]: Sync: ContextShift](
      path: Path,
      blocker: Blocker,
  ): F[Option[Store]] = {
    file
      .exists(blocker, path)
      .ifM(
        file
          .readAll[F](path, blocker, 1024)
          .through(text.utf8Decode)
          .compile
          .string
          .map(decode(_).some),
        none.pure[F],
      )
  }
}

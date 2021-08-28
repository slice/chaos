package zone.slice.chaos
package poller

import cats.effect._
import cats.implicits._
import fs2.io.file.Files
import fs2.io.file.Path

import java.nio.file.{Path => NIOPath}
import fs2.io.file.Flags

object State {
  type Store = Map[String, String]

  def decode(contents: String): Store =
    contents.linesIterator.collect { case s"$selector=$version" =>
      (selector, version)
    }.toMap

  def encode(store: Store): String =
    store
      .map { case selector -> version =>
        s"$selector=$version"
      }
      .mkString("\n")

  def read[F[_]: Concurrent](
      path: NIOPath,
  )(implicit F: Files[F]): F[Option[Store]] = {
    F.exists(Path.fromNioPath(path))
      .ifM(
        F.readAll(Path.fromNioPath(path), 1024, Flags.Read)
          .through(fs2.text.utf8.decode)
          .compile
          .string
          .map(decode(_).some),
        none.pure[F],
      )
  }
}

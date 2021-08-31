package zone.slice.chaos

import cats.effect.Concurrent
import cats.syntax.all.*
import fs2.io.file.{Path, Files}

opaque type State = Map[String, Int]

object State:
  def decode(encoded: String): State =
    encoded.linesIterator
      .collect(
        ({ case s"$selector=$version" =>
          version.toIntOption.map(selector -> _),
        }: String => Option[(String, Int)]).unlift,
      )
      .toMap

  def read[F[_]](path: Path)(using F: Files[F])(using Concurrent[F]): F[State] =
    F.readAll(path).through(fs2.text.utf8.decode).compile.string.map(decode)

  def empty: State = Map.empty

extension (state: State)
  def encode: String =
    state.map(_.toList.mkString("=")).mkString("\n") + "\n"

  def update(label: String, buildNumber: Int): State =
    state + (label -> buildNumber)

  def get(label: String): Option[Int] =
    state.get(label)

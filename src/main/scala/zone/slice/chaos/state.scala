package zone.slice.chaos

import stream.filter1

import cats.Show
import cats.effect.Concurrent
import cats.syntax.all._
import fs2.io.file.{Path, Files}
import fs2.Pipe

class State(val map: Map[String, Int]) extends AnyVal {
  def encode: String =
    map
      .map { case (label, version) => s"$label=$version" }
      .mkString("\n") + "\n"

  def update(label: String, version: Int): State =
    new State(map + (label -> version))

  def get(label: String): Option[Int] =
    map.get(label)

  /** Track a stream of labeled values' latest versions using this state as the
    * initial one.
    */
  def trackLatest[F[_], A](version: A => Int): Pipe[F, (String, A), State] =
    _.map { case label -> value => label -> version(value) }
      .scan(this) { case (state, label -> version) =>
        state.update(label, version)
      }

  /** Remove the first value from a stream if its version matches the one in
    * this state. To use this effectively, you may want to combine it with the
    * `.changes` combinator.
    */
  def deduplicateFirst[F[_], A](label: String)(
    version: A => Int,
  ): Pipe[F, A, A] =
    _.through(
      filter1(value => this.get(label).map(_ != version(value)).getOrElse(true)),
    )
}

object State {
  def decode(encoded: String): State =
    new State(
      encoded.linesIterator
        .collect(
          ({
            case s"$selector=$version" => version.toIntOption.map(selector -> _)
            case _                     => none
          }: String => Option[(String, Int)]).unlift,
        )
        .toMap,
    )

  def read[F[_]: Concurrent](path: Path)(implicit F: Files[F]): F[State] =
    F.readAll(path).through(fs2.text.utf8.decode).compile.string.map(decode)

  def empty: State = new State(Map.empty)

  implicit val show: Show[State] =
    Show.fromToString[Map[String, Int]].contramap((_: State).map)
}

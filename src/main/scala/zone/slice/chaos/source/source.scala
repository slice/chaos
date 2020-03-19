package zone.slice.chaos
package source

import cats._
import cats.effect._
import cats.implicits._
import fs2.Stream

import scala.concurrent.duration._

/**
  * The object emitted by `Source#poll`.
  *
  * It includes the current build alongside the previous one, if any. For the
  * first poll, `previous` will be `None`.
  *
  * @param build the current build
  * @param previous the previous build
  * @tparam B the build type
  */
case class Poll[B](build: B, previous: Option[B])

/**
  * An infinite stream of builds (of type `B`) according to a certain variant
  * (of type `V`).
  *
  * While the build type isn't parameterized on the variant type, it should
  * directly determine the type of build emitted. For example, the variant type
  * could be an algebraic data type representing platforms or branches, which
  * would determine what kind of builds are emitted.
  *
  * The primary benefit that comes from implementing this trait is the
  * `[[poll]]` method, which detects deploys of builds in an infinite stream.
  *
  * @param F the effect type
  * @param B the build type
  */
abstract class Source[F[_], B] {

  /** The variant type. */
  type V

  /* The variant of builds being produced by this source. */
  def variant: V

  /** The stream of builds specialized to the variant. */
  def builds: Stream[F, B]

  /**
    * Polls for builds forever, emitting `[[Poll]]` objects in a stream that
    * represent a "deploy" (a change in the current build).
    *
    * Build comparison is determined using the `cats.Order` type class. A
    * `Right(poll)` object is emitted when a difference is detected. The
    * `[[Poll]]` object encapsulates information about the current and previous
    * build.
    *
    * When an error is thrown, a `Left(throwable)` is emitted; errors do not
    * halt polling.
    *
    * @param rate how long to sleep between pulls
    */
  def poll(
      rate: FiniteDuration,
  )(
      implicit F: Applicative[F],
      O: Order[B],
      T: Timer[F],
  ): Stream[F, Either[Throwable, Poll[B]]] = {
    import scala.collection.immutable.Queue

    // Don't delay twice so that we can immediately emit None, then immediately
    // try for the first build.
    val delayStream: Stream[F, FiniteDuration] =
      Stream(0.seconds).repeatN(2) ++ Stream.awakeDelay[F](rate)
    val buildStream: Stream[F, Option[Either[Throwable, B]]] =
      Stream(none[Either[Throwable, B]]) ++ builds.attempt.map(_.some).repeat
    val delayedBuildStream = delayStream.zipRight(buildStream)

    // Slide over the build stream as we want to emit both the current build
    // and the build before that (for context).
    delayedBuildStream
      .sliding(2)
      .collect {
        // First successful scrape:
        case Queue(None, Some(Right(current))) =>
          Right(Poll(current, None))

        // Two adjacent successful scrapes:
        case Queue(Some(Right(previous)), Some(Right(current)))
            if O.neqv(current, previous) =>
          Right(Poll(current, previous.some))

        // Some error happened:
        case Queue(_, Some(Left(error))) =>
          Left(error)
      }
  }

  // This is needed by `Poller` to deduplicate sources when calculating the
  // source to publisher mapping.
  override def equals(that: Any): Boolean =
    that match {
      case that: Source[F, B] => that.variant == variant
      case _                  => false
    }
}

object Source {
  implicit def eqSource[F[_], V, B]: Eq[Source[F, B]] =
    Eq.fromUniversalEquals
}

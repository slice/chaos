package zone.slice.chaos
package source

import cats._
import cats.effect._
import fs2.Stream

import scala.concurrent.duration._

/**
  * The object given to the callback of `poll`.
  *
  * Contains the current build and the previous build.
  *
  * @param build the current build
  * @param previous the previous build
  * @tparam B the build type
  */
case class PollResult[B](build: B, previous: Option[B])

/**
  * A source of constant builds of certain kinds.
  *
  * Implementing this trait gives you access to `[[poll]]`, allowing you to
  * poll and consume builds indefinitely.
  *
  * @param F the effect type
  * @param B the build type
  */
trait Source[F[_], B] {

  /**
    * The type used for specifying the "kind" of a build.
    *
    * Suitable for indicating a branch, platform, etc. specific to the build
    * being emitted.
    */
  type K

  /** The stream of builds specialized to the kind.
    *
    * @param kind the kind of build
    */
  def builds(kind: K): Stream[F, B]

  /**
    * Scans builds forever, letting the caller tap into new builds as they
    * are detected.
    *
    * "New" builds are determined using the `Order` type class. If a build
    * differs from the previous one (or there wasn't one at all), `onNewBuild`
    * is invoked with a [[PollResult]]. This class encapsulates the new build
    * and the build before it, allowing the caller to tap into additional
    * context.
    *
    * Errors do not halt polling. `onError` is called with any caught exception
    * that gets thrown, and the poller continues after the delay has passed.
    *
    * @param kind the kind of build to poll for
    * @param rate how long to sleep between pulls
    * @param onNewBuild a callback invoked when a new build was detected
    * @param onError a callback invoked when an error was thrown
    */
  def poll(
      kind: K,
      rate: FiniteDuration,
  )(
      onNewBuild: PollResult[B] => F[Unit],
      onError: Throwable => F[Unit],
  )(
      implicit F: Applicative[F],
      O: Order[B],
      T: Timer[F],
  ): Stream[F, Nothing] = {
    // Don't sleep before the first pull.
    (Stream(0.seconds) ++ Stream.awakeDelay[F](rate))
      .zipRight(builds(kind).attempt.repeat)
      .evalScan(Map[K, B]()) {
        case (acc, Left(error)) =>
          F.as(onError(error), acc)
        case (acc, Right(build)) =>
          val prev = acc.get(kind)
          if (prev.forall(O.neqv(_, build)))
            F.as(onNewBuild(PollResult(build, prev)), acc.updated(kind, build))
          else
            F.pure(acc)
      }
      .drain
  }
}

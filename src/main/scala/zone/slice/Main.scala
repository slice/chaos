package zone.slice

import discord.{_, given}

import fs2.Stream
import cats._
import cats.effect._
import cats.effect.std.Console
import cats.syntax.all._

import concurrent.duration._
import fs2.concurrent.Topic
import org.http4s.blaze.client.BlazeClientBuilder

def pick[F[_], A](elems: Seq[A])(using F: Sync[F]): F[A] =
  F.delay {
    val random = new scala.util.Random
    elems.size match
      case 0 => throw java.lang.RuntimeException("dude wtf")
      case 1 => elems(0)
      case n => elems(random.nextInt(n))
  }

/** Submit any changes in a stream to a topic. */
def poll[F[_]: Concurrent, A: Eq](
    things: Stream[F, A],
    topic: Topic[F, A],
): Stream[F, Nothing] =
  things.changes.through(topic.publish).drain

/** Publishing operations that publishers can perform. */
trait Publish[F[_]]:
  def output(text: String): F[Unit]

/** A function that publishes something. */
type Publisher[F[_], -A] = (A, Publish[F]) => F[Unit]

extension [F[_], A](publisher: Publisher[F, A])(using F: Applicative[F])
  def when(cond: A => Boolean): Publisher[F, A] =
    (a, p) => publisher(a, p).whenA(cond(a))

/** Forward things from a topic into a publisher. */
def subscribe[F[_]: Functor, A](topic: Topic[F, A], f: Publisher[F, A])(using
    publish: Publish[F],
): Stream[F, Nothing] =
  topic
    .subscribe(0)
    .evalTap(f(_, publish))
    .drain

def printPublisher[F[_]](prefix: String)(using Monad[F]) =
  (b: FeBuild, p: Publish[F]) =>
    for
      _ <- p.output(s"$prefix: :3")
      _ <- p.output(s"$prefix: $b!")
    yield ()

class Poller[F[_]](using val publish: Publish[F])(using Async[F]):
  // simulate an infinite amount of builds of incrementing version numbers and
  // random branches, each a second apart
  private def builds = Stream
    .unfoldEval(0)(n =>
      pick(Branch.values.to(List))
        .map(b =>
          (
            FeBuild(
              branch = b,
              hash = "",
              number = n,
              assets = AssetBundle.empty,
            ),
            n + 1,
          ).some,
        ),
    )
    .metered(1.second)

  def pollForever: F[Unit] = for
    topic <- Topic[F, FeBuild]
    subscribers = Stream(
      printPublisher("canary build").when(_.branch == Branch.Canary),
      printPublisher("build w/ even version").when(_.number % 2 == 0),
    ).map(subscribe(topic, _))
    consume = subscribers.parJoinUnbounded
    watch   = poll(builds, topic)
    work    = watch concurrently consume
    _ <- work.compile.drain
  yield ()

object Main extends IOApp.Simple:
  def program[F[_]: Async: Console]: F[Nothing] = (for
    httpClient <- BlazeClientBuilder[F](
      concurrent.ExecutionContext.global,
    ).resource
    publish = new Publish[F]:
      def output(text: String): F[Unit] = Console[F].errorln(text)
    poller = Poller[F](using publish)
    program <- Resource.liftK[F](poller.pollForever)
  yield ()).useForever

  def run: IO[Unit] = program[IO]

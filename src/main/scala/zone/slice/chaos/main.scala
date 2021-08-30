package zone.slice.chaos

import discord.{*, given}

import fs2.Stream
import cats.*
import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*

import concurrent.duration.*
import fs2.concurrent.Topic
import org.http4s.blaze.client.BlazeClientBuilder

private def rand[F[_]](min: Int, max: Int)(using F: Sync[F]): F[Int] =
  F.delay((new scala.util.Random).between(min, max + 1))

def printPublisher[F[_]](prefix: String)(using Monad[F]) =
  (b: FeBuild, p: Publish[F]) =>
    for
      _ <- p.output(s"$prefix: :3")
      _ <- p.output(s"$prefix: $b!")
    yield ()

class Poller[F[_]](using publish: Publish[F])(using Async[F]):
  private def fakeBuild(version: Int): FeBuild =
    FeBuild(
      branch = Branch.Canary,
      hash = "???",
      number = version,
      assets = AssetBundle.empty,
    )

  private def builds: Stream[F, FeBuild] =
    Stream
      .eval(rand(10_000, 100_000))
      .flatMap(baseVersion =>
        Stream
          .iterate(baseVersion)(_ + 1)
          .flatMap(version =>
            Stream.eval(rand(2, 6)).flatMap(Stream(version).repeatN(_)),
          ),
      )
      .map(fakeBuild)
      .metered(1.second)

  def pollForever: F[Unit] = for
    topic <- Topic[F, FeBuild]
    subscribers = Stream(
      printPublisher("canary build").when(_.branch == Branch.Canary),
      printPublisher("build w/ even version").when(_.number % 2 == 0),
    ).map(subscribe(topic, _))
    consume = subscribers.parJoinUnbounded
    watch   = poll(builds, topic)
    work    = watch.concurrently(consume)
    _ <- work.compile.drain
  yield ()

val userAgent =
  org.http4s.headers.`User-Agent`(org.http4s.ProductId("chaos", "0.0.0".some))

object Main extends IOApp.Simple:
  def program[F[_]: Async: Console]: F[Nothing] = (for
    executionContext <- Resource.eval(Async[F].executionContext)
    httpClient <- BlazeClientBuilder[F](executionContext)
      .withUserAgent(userAgent)
      .resource
    // can't do this, because of https://github.com/lampepfl/dotty/issues/12646:
    //   given Publish[F] = new Publish[F]:
    //     ...
    //   poller = Poller[F]
    publish = Publish.make[F](console = Console[F], client = httpClient)
    poller  = Poller[F](using publish)
    program <- Resource.eval(poller.pollForever)
  yield ()).useForever

  def run: IO[Unit] = program[IO]

package zone.slice.chaos

import discord.{*, given}

import fs2.Stream
import fs2.io.file.{Path, Files}
import cats.*
import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.circe.*
import io.circe.Json
import io.circe.syntax.*

import concurrent.duration.*
import fs2.concurrent.Topic

private def rand[F[_]](min: Int, max: Int)(using F: Sync[F]): F[Int] =
  F.delay((new scala.util.Random).between(min, max + 1))

def printPublisher[F[_]](prefix: String)(using Monad[F]) =
  (b: FeBuild, p: Publish[F]) => p.output(s"$prefix: $b")

def discordWebhookPublisher[F[_]](webhook: Uri)(using Monad[F], Concurrent[F]) =
  (b: FeBuild, p: Publish[F]) =>
    val embed: Json =
      Json.obj(
        "title"       -> s"${b.branch.humanName} ${b.number}".asJson,
        "description" -> s"Hash: `${b.hash}`".asJson,
        "color"       -> b.branch.color.asJson,
      )
    val body: Json = Json.obj("embeds" -> Json.arr(embed))

    p.post[Json, Unit](webhook, body)

class Poller[F[_]](using publish: Publish[F])(using Async[F]):
  private def fakeBuild(version: Int, branch: Branch): FeBuild =
    FeBuild(
      branch = branch,
      hash = "???",
      number = version,
      assets = AssetBundle.empty,
    )

  private def builds(branch: Branch): Stream[F, FeBuild] = (for
    baseVersion <- Stream.eval(rand(10_000, 100_000))
    version     <- Stream.iterate(baseVersion)(_ + 1)
    repeats     <- Stream.eval(rand(2, 6))
    version     <- Stream(version).repeatN(repeats)
  yield fakeBuild(version, branch)).metered(1.second)

  def pollForever: F[Unit] = for
    topic <- Topic[F, FeBuild]

    subscribers = Stream(
      printPublisher("*** canary build").when(_.branch == Branch.Canary),
      printPublisher("*** build w/ even version").when(_.number % 2 == 0),
    ).map(subscribe(topic, _))
    publish = subscribers.parJoinUnbounded

    statePath = Path("./state.chaos")
    initialState: Option[State] <- Files[F]
      .exists(statePath)
      .ifM(State.read[F](statePath).map(_.some), none[State].pure)
    _ <- Async[F].blocking(println(s"exists? $initialState"))

    scrapers = Stream[F, (String, Stream[F, FeBuild])](
      ("latest canary", builds(Branch.Canary)),
      ("cool stables", builds(Branch.Stable)),
    )
    scrape = scrapers
      .map { case label -> buildStream =>
        val pollStream = poll(buildStream, topic)
        (pollStream.head.filter(build =>
          initialState
            .flatMap(_.get(label))
            .map(_ != build.number)
            .getOrElse(true),
        ) ++ pollStream).map(label -> _)
      }
      .parJoinUnbounded
      .through(trackLatest(initialState.getOrElse(State.empty))(_.number))
      .debug(s => s"latest builds state: $s")
      .map(_.encode)
      .through(continuouslyOverwrite(statePath))

    work = scrape.concurrently(publish)
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

package zone.slice.chaos

import discord._
import publish._
import poll._
import io._

import fs2.Stream
import fs2.io.file.{Path, Files}
import cats.effect._
import cats.effect.std.{Console, Random}
import cats.syntax.all._
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.circe._
import _root_.io.circe.Json
import _root_.io.circe.syntax._

import concurrent.duration._
import fs2.concurrent.Topic

object publishers {
  def printPublisher[F[_]](prefix: String): Publisher[F, FeBuild] =
    (b: FeBuild, p: Publish[F]) => p.output(s"$prefix: $b")

  def discordWebhookPublisher[F[_]: Concurrent](
    webhook: Uri,
  ): Publisher[F, FeBuild] =
    (b: FeBuild, p: Publish[F]) => {
      val embed: Json =
        Json.obj(
          "title"       -> s"${b.branch.humanName} ${b.number}".asJson,
          "description" -> s"Hash: `${b.hash}`".asJson,
          "color"       -> b.branch.color.asJson,
        )
      val body: Json = Json.obj("embeds" -> Json.arr(embed))

      p.post[Json, Unit](webhook, body)
    }
}

import publishers._

class Poller[F[_]](implicit
  publish: Publish[F],
  F: Async[F],
  C: Console[F],
  random: Random[F],
) {
  private def fakeBuild(version: Int, branch: Branch): FeBuild =
    FeBuild(
      branch = branch,
      hash = "???",
      number = version,
      assets = AssetBundle.empty,
    )

  private def builds(branch: Branch): Stream[F, FeBuild] = (for {
    baseVersion <- Stream.eval(random.betweenInt(10_000, 100_000 + 1))
    version     <- Stream.iterate(baseVersion)(_ + 1)
    repeats     <- Stream.eval(random.betweenInt(2, 6 + 1))
    version     <- Stream(version).repeatN(repeats.toLong)
  } yield fakeBuild(version, branch)).metered(1.second)

  def pollForever: F[Unit] = for {
    topic <- Topic[F, FeBuild]

    subscribers = Stream(
      printPublisher[F]("*** canary build").when(_.branch == Branch.Canary),
      printPublisher[F]("*** build w/ even version").when(_.number % 2 == 0),
    )

    consumeAndPublish = subscribers
      .map(stream => subscribe[F, FeBuild](topic, stream))
      .parJoinUnbounded

    statePath = Path("./state.chaos")
    initialState: State <- Files[F]
      .exists(statePath)
      .ifM(State.read[F](statePath).map(_.some), none[State].pure[F])
      .map(_.getOrElse(State.empty))
    _ <- C.println(s"*** initial state: $initialState")

    scrapers = Stream[F, (String, Stream[F, FeBuild])](
      ("latest canary", builds(Branch.Canary)),
      ("cool stables", builds(Branch.Stable)),
    )
    scrape = scrapers
      .through(dedup1FromState(initialState)(_.number))
      .map { case label -> builds =>
        // throw away build stream labels when submitting to the topic
        val topic2 = topic.imap[(String, FeBuild)](("", _))(_._2)
        // inject labels into the build stream
        builds.map(build => label -> build).through(poll(topic2))
      }
      .parJoinUnbounded
      .through(initialState.trackLatest(_.number))
      .debug(s => s"latest builds state: $s")
      .map(_.encode)
      .through(continuouslyOverwrite(statePath))

    work = scrape.concurrently(consumeAndPublish)
    _ <- work.compile.drain
  } yield ()
}

object Main extends IOApp.Simple {
  val userAgent =
    org.http4s.headers.`User-Agent`(org.http4s.ProductId("chaos", "0.0.0".some))

  def program[F[_]: Async: Console]: F[Nothing] = (for {
    executionContext <- Resource.eval(Async[F].executionContext)
    httpClient <- BlazeClientBuilder[F](executionContext)
      .withUserAgent(userAgent)
      .resource
    implicit0(publish: Publish[F]) = Publish.make[F](
      console = Console[F],
      client = httpClient,
    )
    implicit0(random: Random[F]) <- Resource.eval(Random.scalaUtilRandom[F])
    poller = new Poller[F]
    _ <- Resource.eval(poller.pollForever)
  } yield ()).useForever

  def run: IO[Unit] = program[IO]
}

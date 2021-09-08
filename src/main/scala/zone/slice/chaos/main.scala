package zone.slice.chaos

import config._
import discord._
import publish._
import io._
import stream.FallibleStream

import fs2.Stream
import fs2.io.file.{Path, Files}
import fs2.concurrent.Topic
import cats.effect._
import cats.effect.std.{Console, Random}
import cats.syntax.all._
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.circe._
import _root_.io.circe.Json
import _root_.io.circe.literal._
import pureconfig._
import pureconfig.module.catseffect.syntax._

object publishers {
  def printPublisher[F[_]](prefix: String): Publisher[F, FeBuild] =
    (b: FeBuild, p: Publish[F]) => p.output(s"$prefix: $b")

  def discordWebhookPublisher[F[_]: Concurrent](
    webhook: Uri,
  ): Publisher[F, FeBuild] =
    (b: FeBuild, p: Publish[F]) => {
      val embed = json"""
        {
          "title": ${s"${b.branch.humanName} ${b.number}"},
          "description": ${s"Hash: `${b.hash}`"},
          "color": ${b.branch.color}
        }
      """
      val body = json"""{"embeds": [$embed]}"""

      p.post[Json, Unit](webhook, body)
    }
}

import publishers._

class Poller[F[_]](config: ChaosConfig)(implicit
  publish: Publish[F],
  F: Async[F],
  C: Console[F],
) {
  def pollForever: F[Unit] = for {
    topic <- Topic[F, FeBuild]

    statePath = Path("./state.chaos")
    initialState: State <- Files[F]
      .exists(statePath)
      .ifM(State.read[F](statePath).map(_.some), none[State].pure[F])
      .map(_.getOrElse(State.empty))
    _ <- C.println(s"*** initial state: ${initialState.map}")

    subscribers = Stream(
      // print all new builds to stdout:
      printPublisher[F]("***** NEW BUILD"),
      // showing off conditionalization with .when:
      printPublisher[F]("    * canary build").when(_.branch == Branch.Canary),
      printPublisher[F]("    * build w/ even version").when(_.number % 2 == 0),
    )
    // subscribe to the build topic
    consumeAndPublish = subscribers
      .map(stream => subscribe[F, FeBuild](topic, stream))
      .parJoinUnbounded

    implicit0(random: Random[F]) <- Random.scalaUtilRandom[F]
    scrapers = Stream[F, (String, FallibleStream[F, FeBuild])](
      // labelled build streams; the labels are used to keep track of latest
      // versions in the state file so we don't republish on a program restart
      (
        "canary",
        FeBuilds[F](Branch.Canary).meteredStartImmediately(config.interval),
      ),
      ("fake canary", FeBuilds.fake(Branch.Canary)),
      ("fake stable", FeBuilds.fake(Branch.Stable)),
    )
    scrape = scrapers
      .map { case label -> builds =>
        builds
          .evalMapFilter {
            case Left(error) =>
              C.errorln(s"Build stream (\"$label\") errored: $error")
                .as(none[FeBuild])
            case Right(r) => r.some.pure[F]
          }
          // remove duplicate builds
          .changes
          // remove the first build if the version is the same as the one we
          // had saved on disk
          .through(initialState.deduplicateFirst(label)(_.number))
          // publish to the build topic
          .evalTap(topic.publish1)
          .map(label -> _)
      }
      .parJoinUnbounded
      // continuously update the state, tracking the latest builds
      .through(initialState.trackLatest(_.number))
      .map(_.encode)
      .through(continuouslyOverwrite(statePath))

    // now scrape and publish at the same time
    work = scrape.concurrently(consumeAndPublish)
    _ <- work.compile.drain
  } yield ()
}

object Main extends IOApp.Simple {
  val userAgent =
    org.http4s.headers.`User-Agent`(org.http4s.ProductId("chaos", "0.0.0".some))

  def loadConfig[F[_]: Sync]: F[ChaosConfig] =
    ConfigSource.defaultApplication.loadF[F, ChaosConfig]()

  def program[F[_]: Async: Console]: F[Nothing] = (for {
    executionContext <- Resource.eval(Async[F].executionContext)
    httpClient <- BlazeClientBuilder[F](executionContext)
      .withUserAgent(userAgent)
      .resource
    implicit0(publish: Publish[F]) = Publish.make[F](
      console = Console[F],
      client = httpClient,
    )
    config <- Resource.eval(loadConfig)
    _ <- Resource.eval(Console[F].errorln(s"*** config: $config"))
    poller = new Poller[F](config)
    _ <- Resource.eval(poller.pollForever)
  } yield ()).useForever

  def run: IO[Unit] = program[IO]
}

package zone.slice.chaos

import config._
import discord._
import publish._
import io._
import stream._
import select.transform._

import fs2.Stream
import fs2.io.file.{Path, Files}
import fs2.concurrent.Topic
import cats.effect._
import cats.effect.std.{Console, Random}
import cats.syntax.all._
import org.http4s.blaze.client.BlazeClientBuilder
import pureconfig._
import pureconfig.module.catseffect.syntax._

class Poller[F[_]](buildTopic: Topic[F, FeBuild], config: ChaosConfig)(implicit
  publish: Publish[F],
  F: Async[F],
  C: Console[F],
) {
  def pollAndPublish(
    initialState: State,
    labeledBuildStreams: Stream[F, Labeled[BuildStream[F]]],
  ): Stream[F, Labeled[FeBuild]] =
    labeledBuildStreams.map { case label -> builds =>
      builds
        .evalMapFilter {
          case Left(error) =>
            C.errorln(s"""Build stream ("$label") errored.""") *>
              C.printStackTrace(error).as(none[FeBuild])
          case Right(build) => build.some.pure[F]
        }
        .changes
        .through(initialState.deduplicateFirst(label)(_.number))
        .evalTap(buildTopic.publish1)
        .map(label -> _)
    }.parJoinUnbounded

  def determineInitialState(path: Path): F[State] =
    Files[F]
      .exists(path)
      .ifM(State.read[F](path).map(_.some), none[State].pure[F])
      .map(_.getOrElse(State.empty))

  def makePublishers: Vector[Publisher[F, FeBuild]] =
    config.publishers.map { publisherConfig =>
      import PublisherConfig._
      import zone.slice.chaos.publish.publishers._
      (publisherConfig match {
        case Discord(uri, _)  => discordWebhookPublisher[F](uri)
        case Print(format, _) => printPublisher[F](format)
      }).when(selectConditions(publisherConfig.scrape).getOrElse { _ =>
        false
      })
    }

  def makeBuildStreams: F[Vector[Labeled[BuildStream[F]]]] =
    F.fromEither(
      config.publishers
        .flatMap(_.scrape)
        .traverse(selectBuildStreams(_, config.interval))
        .map(_.flatten.distinctBy(_._1)),
    )

  def pollForever: F[Unit] = for {
    initialState <- determineInitialState(Path("./state.chaos"))
    _            <- C.println(s"*** initial state: ${initialState.map}")

    publishers = makePublishers
    _ <- C.println(s"[config] publishers: $publishers")
    // subscribe to the build topic
    consumeAndPublish = Stream
      .emits(publishers)
      .map(stream => subscribe[F, FeBuild](buildTopic, stream))
      .parJoinUnbounded

    implicit0(random: Random[F]) <- Random.scalaUtilRandom[F]
    labeledBuildStreams          <- makeBuildStreams
    _ <- C.println(
      s"[config] build streams: ${labeledBuildStreams.map(_._1).mkString(", ")}",
    )
    scrape = pollAndPublish(initialState, Stream.emits(labeledBuildStreams))
      .through(initialState.trackLatest(_.number))
      .map(_.encode)
      .through(continuouslyOverwrite(config.stateFilePath))

    // now scrape and publish at the same time
    work = scrape.concurrently(consumeAndPublish)
    _ <- work.compile.drain
  } yield ()
}

object Poller {
  def loadConfig[F[_]: Sync]: F[ChaosConfig] =
    ConfigSource.defaultApplication.loadF[F, ChaosConfig]()

  def apply[F[_]: Async: Console: Publish]: F[Poller[F]] = for {
    config <- loadConfig
    topic  <- Topic[F, FeBuild]
    poller = new Poller(buildTopic = topic, config = config)
  } yield poller
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
    _ <- Resource.eval(Poller[F].flatMap(_.pollForever))
  } yield ()).useForever

  def run: IO[Unit] = program[IO]
}

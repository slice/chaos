package zone.slice.chaos
package poller

import publisher._
import discord._
import Branch._
import poller._
import source._
import source.discord._

import io.chrisdavenport.log4cats.testing.TestingLogger
import cats.implicits._
import cats.effect._

import scala.concurrent.duration._

class PollerSpec extends ChaosSpec {
  trait PollerFixture extends LoggingFixture with OkHttpClientFixture {
    private val formatString = "new $branch; $build_number"
    val publisher            = new StdoutPublisher[IO](formatString)
    val publisherSetting =
      StdoutPublisherSetting(formatString, Set("fe:canary"))
    val config =
      Config(interval = 1.second, publishers = List(publisherSetting))
    val poller      = new Poller[IO](config)
    val spiedPoller = spy(poller)
    val fakeSource  = mock[Source[IO, Build]]
  }

  def makeBuild(number: Int): Build =
    Build(Canary, "", number, AssetBundle.empty)
  def makeDeploy(buildNumber: Int): Deploy =
    Deploy(makeBuild(buildNumber), false)

  "poller" - {
    "taps from a source" in new PollerFixture {
      val current  = makeBuild(100)
      val previous = makeBuild(50)

      spiedPoller
        .pollTap(fakeSource, Set(publisher))(
          Right(Poll(current, previous.some)),
        )
        .unsafeRunSync()
      spiedPoller.publish(Deploy(current, isRevert = false), Set(publisher)) wasCalled once
    }

    "detects reverts" in new PollerFixture {
      val current = makeBuild(1)

      forAll(Seq(makeBuild(100).some, none)) { previous =>
        spiedPoller
          .pollTap(fakeSource, Set(publisher))(Right(Poll(current, previous)))
          .unsafeRunSync()
        spiedPoller.publish(Deploy(current, isRevert = true), Set(publisher)) wasCalled once
      }
    }

    // The following test crashes scalac. Nice.

    // "logs polling errors" in new PollerFixture {
    //   val exception = new Exception("i'm green da ba dee")

    //   val source = mock[FrontendSource[IO]]
    //   import fs2.Stream
    //   Stream.raiseError[IO](exception) willBe returned by source.poll(*, *)(*)

    //   spiedPoller.frontendPoller(Set(Canary), source).unsafeRunTimed(250.millis)

    //   val message =
    //     TestingLogger.ERROR(
    //       show"Failed to poll scrape ${Canary}",
    //       Some(exception),
    //     )
    //   logged.unsafeRunSync() should contain(message)
    // }

    "logs publishing errors" in new PollerFixture {
      val exception = new Exception("i'm blue da ba dee")
      val build     = makeBuild(100)
      val deploy    = Deploy(build, isRevert = false)

      // Force Poller#publish to raise an error.
      spiedPoller.publish(deploy, Set(publisher)) returns IO.raiseError(
        exception,
      )

      spiedPoller
        .pollTap(fakeSource, Set(publisher))(Right(Poll(build, none)))
        .unsafeRunSync()

      val message =
        TestingLogger.ERROR(show"Failed to publish $build", Some(exception))
      logged.unsafeRunSync() should contain(message)
    }

    "publishes" in new PollerFixture {
      val deploy = makeDeploy(100)

      val fakePublisher = mock[Publisher[IO]]
      fakePublisher.publish(deploy) returns IO.unit

      spiedPoller
        .publish(deploy, Set(fakePublisher))
        .unsafeRunSync()

      fakePublisher.publish(deploy) wasCalled once
    }

    "resolves" in new PollerFixture {
      implicit val fakeClient = mock[org.http4s.client.Client[IO]]

      val stdoutSetting    = StdoutPublisherSetting("aah", Set("fe:*"))
      val discordSetting   = DiscordPublisherSetting(0, "", Set("fe:canary"))
      val stdoutPublisher  = spiedPoller.buildPublisher(stdoutSetting)
      val discordPublisher = spiedPoller.buildPublisher(discordSetting)
      val settings         = List(stdoutSetting, discordSetting)

      spiedPoller.resolve(settings).toSet shouldBe Map(
        FrontendSource(Canary, fakeClient) -> Set(
          stdoutPublisher,
          discordPublisher,
        ),
        FrontendSource(PTB, fakeClient)    -> Set(stdoutPublisher),
        FrontendSource(Stable, fakeClient) -> Set(stdoutPublisher),
      ).toSet
    }
  }
}

package zone.slice.chaos
package poller

import publisher._
import discord._
import Branch._
import poller._
import source._

import io.chrisdavenport.log4cats.testing.TestingLogger
import cats.implicits._
import cats.effect._

import scala.concurrent.duration._

class PollerSpec extends ChaosSpec {
  trait PollerFixture extends LoggingFixture with OkHttpClientFixture {
    val publisher =
      StdoutPublisherSetting("[test] $branch $build_number", Set(Canary))
    val config      = Config(interval = 1.second, publishers = List(publisher))
    val poller      = new Poller[IO](config)
    val spiedPoller = spy(poller)
  }

  def makeBuild(buildNumber: Int): Build =
    Build(Canary, "", buildNumber, AssetBundle.empty)
  def makeDeploy(buildNumber: Int): Deploy =
    Deploy(makeBuild(buildNumber), false)

  "poller" - {
    "taps from a source" in new PollerFixture {
      val current  = makeBuild(100)
      val previous = makeBuild(50)

      spiedPoller.pollTap(PollResult(current, previous.some)).run()
      spiedPoller.publish(Deploy(current, isRevert = false), List(publisher)) wasCalled once
    }

    "detects reverts" in new PollerFixture {
      val current = makeBuild(1)

      forAll(Seq(makeBuild(100).some, none)) { previous =>
        spiedPoller.pollTap(PollResult(current, previous)).run()
        spiedPoller.publish(Deploy(current, isRevert = true), List(publisher)) wasCalled once
      }
    }

    // The following test crashes scalac. Nice.

    // "logs polling errors" in new PollerFixture {
    //   val exception = new Exception("i'm green da ba dee")

    //   val source = mock[DiscordFrontendSource[IO]]
    //   import fs2.Stream
    //   Stream.raiseError[IO](exception) willBe returned by source.poll(*, *)(*)

    //   spiedPoller.frontendPoller(Set(Canary), source).unsafeRunTimed(250.millis)

    //   val message =
    //     TestingLogger.ERROR(
    //       show"Failed to poll scrape ${Canary}",
    //       Some(exception),
    //     )
    //   logged.run() should contain(message)
    // }

    "logs publishing errors" in new PollerFixture {
      val exception = new Exception("i'm blue da ba dee")
      val build     = makeBuild(100)
      val deploy    = Deploy(build, isRevert = false)

      // Force Poller#publish to raise an error.
      spiedPoller.publish(deploy, List(publisher)) returns IO.raiseError(
        exception,
      )

      spiedPoller.pollTap(PollResult(build, none)).run()

      val message =
        TestingLogger.ERROR(show"Failed to publish $build", Some(exception))
      logged.run() should contain(message)
    }

    "publishes" in new PollerFixture {
      val deploy = makeDeploy(100)

      val fakePublisherSetting = mock[PublisherSetting]
      fakePublisherSetting.branches returns Set(Canary)
      val fakePublisher = mock[Publisher[IO]]
      fakePublisher.publish(deploy) returns IO.unit

      // Make Poller#buildPublisher return fakePublisher when given
      // fakePublisherSetting.
      //
      // NOTE: using willBe to stub here because actually calling it would throw.
      fakePublisher willBe returned by spiedPoller.buildPublisher(
        fakePublisherSetting,
      )

      spiedPoller
        .publish(deploy, List(fakePublisherSetting))
        .run()
      fakePublisher.publish(deploy) wasCalled once
    }
  }
}

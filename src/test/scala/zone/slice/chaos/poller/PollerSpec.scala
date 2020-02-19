package zone.slice.chaos
package poller

import publisher._
import discord._
import Branch._
import poller._

import io.chrisdavenport.log4cats.testing.TestingLogger
import fs2.Stream
import cats.implicits._
import cats.effect._
import org.scalatest.Inspectors

import scala.concurrent.duration._

class PollerSpec extends ChaosSpec {
  trait PollerFixture extends LoggingFixture with OkHttpClientFixture {
    val publisher =
      StdoutPublisherSetting("[test] $branch $build_number", Set(Canary))
    val config = Config(interval = 1.second, publishers = List(publisher))
    val poller = new Poller[IO](config)
  }

  def makeBuild(buildNumber: Int): Build =
    Build(Canary, "", buildNumber, AssetBundle.empty)
  def makeDeploy(buildNumber: Int): Deploy =
    Deploy(makeBuild(buildNumber), false)

  "poller" - {
    "scans a build map" in new PollerFixture {
      Inspectors.forAll(List(none, 100.some, 1000.some)) { value =>
        val map: BuildMap.Type = Map(Canary -> value)
        val build              = makeBuild(500)

        poller.consumeBuild(map, build).run() should contain(Canary -> 500.some)
      }
    }

    "detects reverts" in new PollerFixture {
      val spiedPoller = spy(poller)
      val build       = makeBuild(100)

      spiedPoller.consumeBuild(Map(Canary -> Some(200)), build)
      spiedPoller.publish(Deploy(build, isRevert = true), List(publisher)) wasCalled once
    }

    "logs polling errors" in new PollerFixture {
      val exception   = new Exception("something went wrong")
      val spiedPoller = spy(poller)

      // Stub scrape stream to only return an error.
      val stream: Stream[IO, (Branch, Either[Throwable, Build])] =
        Stream((Canary, Left(exception))).covary[IO]
      spiedPoller.scrapeStream(Set(Canary), 1.second) returns stream

      // Run poller so that it can log the exception.
      spiedPoller.poller.compile.drain.run()

      val message =
        TestingLogger.ERROR("Failed to scrape Canary", Some(exception))
      logged.run() should contain(message)
    }

    "logs publishing errors" in new PollerFixture {
      val exception   = new Exception("i'm blue da ba dee")
      val spiedPoller = spy(poller)
      val build       = makeBuild(100)
      val deploy      = Deploy(build, isRevert = false)

      // Force Poller#publish to raise an error.
      spiedPoller.publish(deploy, List(publisher)) returns IO.raiseError(
        exception,
      )

      spiedPoller.consumeBuild(BuildMap.default, build).run()

      val message =
        TestingLogger.ERROR(show"Failed to publish $build", Some(exception))
      logged.run() should contain(message)
    }

    "publishes" in new PollerFixture {
      val spiedPoller = spy(poller)
      val deploy      = makeDeploy(100)

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

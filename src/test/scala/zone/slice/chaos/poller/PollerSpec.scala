package zone.slice.chaos
package poller

import publisher._
import discord._
import Branch._
import poller._
import source._
import source.discord._

// import io.chrisdavenport.log4cats.testing.TestingLogger
// import cats._
import cats.implicits._
import cats.effect._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class PollerSpec extends ChaosSpec {
  trait PollerFixture extends LoggingFixture with OkHttpClientFixture {
    private val formatString = "new $branch; $build_number"
    val publisher            = new StdoutPublisher[IO](formatString)
    val publisherSetting =
      StdoutPublisherSetting(formatString, Set("fe:canary"))
    val config =
      Config(interval = 1.second, publishers = List(publisherSetting))
    val blocker     = Blocker.liftExecutionContext(ExecutionContext.global)
    val poller      = new Poller[IO](config, blocker)
    val spiedPoller = spy(poller)
    val fakeSource  = mock[Source[IO, Build]]
  }

  def makeBuild(number: Int): FrontendBuild =
    FrontendBuild(Canary, "", number, AssetBundle.empty)
  def makeDeploy(buildNumber: Int): Deploy =
    Deploy(makeBuild(buildNumber), false)

  "poller" - {
    "resolves" in new PollerFixture {
      implicit val fakeClient = mock[org.http4s.client.Client[IO]]

      val stdoutSetting    = StdoutPublisherSetting("aah", Set("fe:*"))
      val discordSetting   = DiscordPublisherSetting(0, "", Set("fe:canary"))
      val stdoutPublisher  = spiedPoller.buildPublisher(stdoutSetting)
      val discordPublisher = spiedPoller.buildPublisher(discordSetting)
      val settings         = List(stdoutSetting, discordSetting)

      spiedPoller.resolve(settings).toSet shouldBe Map(
        SelectedSource("fe:canary", FrontendSource(Canary, fakeClient)) -> Set(
          stdoutPublisher,
          discordPublisher,
        ),
        SelectedSource("fe:ptb", FrontendSource(PTB, fakeClient)) -> Set(
          stdoutPublisher,
        ),
        SelectedSource("fe:stable", FrontendSource(Stable, fakeClient)) -> Set(
          stdoutPublisher,
        ),
      ).toSet
    }
  }
}

package zone.slice.chaos

import discord._
import stream._
import publish._

import cats.syntax.all._
import cats.effect._
import scala.concurrent.duration._

package object select {
  private type BuildCond = FeBuild => Boolean

  def selectCondition(selector: String): Option[BuildCond] =
    selector match {
      case s"fe:$branch" =>
        Select[Branch].select(branch).map { branch => _.branch == branch }
      case _ => none
    }

  def selectConditions(selectors: Vector[String]): Option[BuildCond] =
    selectors
      .traverse(selectCondition)
      .map(_.sequence.map(_.forall(identity)))

  def selectBuildStream[F[_]: Publish: Temporal](
    selector: String,
    every: FiniteDuration,
  ): Option[(String, FallibleStream[F, FeBuild])] =
    (selector match {
      case s"fe:$branch" =>
        Select[Branch].select(branch).map { branch =>
          (selector, FeBuilds[F](branch))
        }
      case _ => none
    }).map { case (label, stream) =>
      (label, stream.meteredStartImmediately(every))
    }

  def selectBuildStreams[F[_]: Publish: Temporal](
    selector: Vector[String],
    every: FiniteDuration,
  ): Option[Vector[(String, FallibleStream[F, FeBuild])]] =
    selector.traverse(selectBuildStream(_, every)).map { labeledStreams =>
      // Deduplicate build streams according to their label. If two publishers
      // scrape from `fe:canary`, then we should only have one build stream
      // scraping from that branch.
      labeledStreams.distinctBy(_._1)
    }
}

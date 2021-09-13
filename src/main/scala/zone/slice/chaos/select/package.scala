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
        val branches = Select[Branch].multiselect(branch)
        if (branches.isEmpty) none
        else { (build: FeBuild) => branches.contains(build.branch) }.some
      case _ => none
    }

  def selectConditions(selectors: Vector[String]): Option[BuildCond] =
    selectors
      .traverse(selectCondition)
      .map(_.sequence.map(_.forall(identity)))

  def selectBuildStreams[F[_]: Publish: Temporal](
    selector: String,
    every: FiniteDuration,
  ): Option[Vector[(String, FallibleStream[F, FeBuild])]] =
    (selector match {
      case s"fe:$branch" =>
        Select[Branch]
          .canonicalize(branch)
          .toVector
          .map { canonSelector =>
            Select[Branch].select(canonSelector).map((canonSelector, _))
          }
          .sequence
          .map(_.map { case (canonSelector, branch) =>
            (s"fe:$canonSelector", FeBuilds[F](branch))
          })
      case _ => Vector.empty.some
    }).map(_.map { case (label, buildStream) =>
      (label, buildStream.meteredStartImmediately(every))
    })
}

package zone.slice.chaos

import discord._
import stream._
import publish._

import cats.syntax.all._
import cats.effect._
import scala.concurrent.duration._

package object select {
  private type BuildCond = FeBuild => Boolean

  private def setToOptionalVector[A](set: Set[A]): Option[Vector[A]] =
    if (set.isEmpty) none
    else set.toVector.some

  def selectCondition(selector: String): Option[BuildCond] =
    selector match {
      case s"fe:$branch" =>
        val branches = Select[Branch].multiselect(branch)
        if (branches.isEmpty) none
        else { (build: FeBuild) =>
          branches.map(_.value).contains(build.branch)
        }.some
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
        setToOptionalVector(Select[Branch].multiselect(branch))
          .map(_.map { case selected =>
            (s"fe:${selected.selector}", FeBuilds[F](selected.value))
          })
      case _ => Vector.empty.some
    }).map(_.map { case (label, buildStream) =>
      (label, buildStream.meteredStartImmediately(every))
    })
}
package zone.slice.chaos
package select

import discord._
import stream._
import publish._

import cats.syntax.all._
import cats.effect._
import scala.concurrent.duration._

object transform {
  private type BuildCond = FeBuild => Boolean

  sealed abstract class SelectorTransformException
      extends Exception
      with Product
      with Serializable {
    def message: String

    override def getMessage = message
  }

  object SelectorTransformationException {
    final case class InvalidSelector(message: String)
        extends SelectorTransformException

    final case class InvalidSource(message: String)
        extends SelectorTransformException

    final case class InvalidVariant(message: String)
        extends SelectorTransformException
  }

  import SelectorTransformationException._

  sealed abstract class Source extends Product with Serializable {
    def name: String
  }

  object Source {
    case object Frontend extends Source {
      val name: String = "fe"
    }
  }

  import Source._

  private def setToOptionalVector[A](set: Set[A]): Option[Vector[A]] =
    if (set.isEmpty) none
    else set.toVector.some

  /** Resolves a frontend source selector's variant into a vector of selected
    * frontend branches.
    */
  def multiselectBranches(
    selector: String,
  ): Either[SelectorTransformException, Vector[Selected[Branch]]] =
    Either.fromOption(
      setToOptionalVector(Select[Branch].multiselect(selector)),
      InvalidVariant(s"Unable to select a frontend branch from: \"$selector\""),
    )

  /** Resolves a selector's source and variant. */
  def resolveSelectorSource(
    selector: String,
  ): Either[SelectorTransformException, (Source, String)] =
    selector.split(':').toList match {
      case Frontend.name :: variant :: Nil =>
        (Frontend, variant).asRight
      case source :: _ :: Nil =>
        InvalidSource(
          s"Unknown source: \"$source\" in selector \"$selector\"",
        ).asLeft
      case source :: Nil =>
        InvalidSource(s"Selector is missing its variant: \"$source\"").asLeft
      case Nil =>
        InvalidSelector(s"Empty selector: \"$selector\"").asLeft
      case _ =>
        InvalidSelector(
          (s"|Invalid selector syntax: \"$selector\". " +
            "|Only one colon is expected.").stripMargin,
        ).asLeft
    }

  /** Transforms a selector into a build condition. */
  def selectCondition(
    selector: String,
  ): Either[SelectorTransformException, BuildCond] =
    resolveSelectorSource(selector).flatMap { case (Frontend, variant) =>
      multiselectBranches(variant).map { selectedBranches => (build) =>
        selectedBranches.map(_.value).contains(build.branch)
      }
    }

  /** A companion method to [[selectCondition]] that transforms a vector of
    * selectors into a single build condition. All of the selectors must be
    * satisfied for the condition to pass.
    */
  def selectConditions(
    selectors: Vector[String],
  ): Either[SelectorTransformException, BuildCond] =
    selectors
      .traverse(selectCondition)
      .map(_.sequence.map(_.forall(identity)))

  /** Transforms a selector into a vector of labeled build streams. */
  def selectBuildStreams[F[_]: Publish: Temporal](
    selector: String,
    every: FiniteDuration,
  ): Either[SelectorTransformException, Vector[Labeled[BuildStream[F]]]] =
    resolveSelectorSource(selector)
      .flatMap { case (source @ Frontend, variant) =>
        multiselectBranches(variant).map(_.map { case selected =>
          (s"${source.name}:${selected.selector}", FeBuilds[F](selected.value))
        })
      }
      .map(_.map { case (label, buildStream) =>
        (label, buildStream.meteredStartImmediately(every))
      })
}

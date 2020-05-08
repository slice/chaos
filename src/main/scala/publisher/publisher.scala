package zone.slice.chaos

import cats._
import cats.data.Kleisli
import discord.Deploy

trait Publisher[F[_]] {
  def publish: Kleisli[F, Deploy, Unit]
}

object Publisher {
  implicit def publisherMonoid[F[_]](implicit
      ev: Monoid[Kleisli[F, Deploy, Unit]],
  ): Monoid[Publisher[F]] =
    new Monoid[Publisher[F]] {
      def empty =
        new Publisher[F] {
          val publish = ev.empty
        }

      def combine(x: Publisher[F], y: Publisher[F]) =
        new Publisher[F] {
          val publish = ev.combine(x.publish, y.publish)
        }
    }
}

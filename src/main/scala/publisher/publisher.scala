package zone.slice.chaos

import cats.Monoid
import cats.data.Kleisli
import discord.Deploy

trait Publisher[F[_]] {
  def publish: Kleisli[F, Deploy, Unit]
}

object Publisher {
  implicit def publisherMonoid[F[_]](implicit
      kleisliMonoid: Monoid[Kleisli[F, Deploy, Unit]],
  ): Monoid[Publisher[F]] =
    new Monoid[Publisher[F]] {
      def empty =
        new Publisher[F] {
          val publish = kleisliMonoid.empty
        }

      def combine(x: Publisher[F], y: Publisher[F]) =
        new Publisher[F] {
          val publish = kleisliMonoid.combine(x.publish, y.publish)
        }
    }
}

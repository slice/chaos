package zone.slice.chaos
package publisher

import discord.Build

import cats.data.EitherT

/**
  * Publishes a fresh Discord client build somewhere.
  *
  * In practice, these are used to notify consumers of a new build (for example,
  * a Discord webhook).
  */
trait Publisher[F[_], E <: Exception] {
  def publish(build: Build): EitherT[F, E, Unit]
}

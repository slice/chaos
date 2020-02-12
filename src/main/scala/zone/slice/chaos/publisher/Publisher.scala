package zone.slice.chaos
package publisher

import discord.Build

/**
  * Publishes a fresh Discord client build somewhere.
  *
  * In practice, these are used to notify consumers of a new build (for example,
  * a Discord webhook).
  */
trait Publisher[F[_]] {
  def publish(build: Build): F[Unit]
}

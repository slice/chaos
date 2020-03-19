package zone.slice.chaos
package publisher

import discord.Deploy

/**
  * Publishes a fresh [[discord.Deploy]] somewhere.
  *
  * In practice, these are used to notify consumers of a new build (for example,
  * a Discord webhook). A deploy object is published instead of a build object
  * because a deploy object contains additional context that the receiver might
  * be interested in.
  */
trait Publisher[F[_]] {
  def publish(deploy: Deploy): F[Unit]
}

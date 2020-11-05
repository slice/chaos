package zone.slice.chaos

import discord.Deploy

trait Publisher[F[_]] {
  def publish(deploy: Deploy): F[Unit]
}

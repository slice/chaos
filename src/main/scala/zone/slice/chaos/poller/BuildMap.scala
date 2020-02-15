package zone.slice.chaos
package poller

import discord.Branch

object BuildMap {
  type Type = Map[Branch, Option[Int]]

  def default: Type = Branch.all.map(_ -> None).toMap
}

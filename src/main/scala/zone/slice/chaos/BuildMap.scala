package zone.slice.chaos

import discord.Branch

object BuildMap {
  type Type = Map[Branch, Option[Int]]

  def default: Type = Branch.all.map(_ -> None).toMap
}

package zone.slice.chaos
package source

import discord._
import scraper._

import fs2.Stream

/**
  * A [[Source]] for Discord frontend builds.
  *
  * This class is a light wrapper over a [[scraper.Scraper Scraper]]. That class
  * actually does the work of creating [[discord.Build Build]] objects.
  *
  * @param scraper the scraper
  */
class DiscordFrontendSource[F[_]](val scraper: Scraper[F])
    extends Source[F, Build] {
  type K = Branch

  def builds(branch: Branch): Stream[F, Build] =
    Stream.repeatEval(scraper.scrape(branch))
}

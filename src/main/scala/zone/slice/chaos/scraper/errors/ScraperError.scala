package zone.slice.chaos.scraper.errors

sealed trait ScraperError

object ScraperError {
  final case class Download(error: DownloadError) extends ScraperError
  final case class Extractor(error: ExtractorError) extends ScraperError
}

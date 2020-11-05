package zone.slice.chaos
package publisher

import discord._

import cats.effect.Sync

case class StdoutPublisher[F[_]: Sync](format: String) extends Publisher[F] {

  val replacers: Deploy => Map[String, String] = (deploy: Deploy) => {
    val build = deploy.build

    val buildProperties = deploy.build match {
      case build: FrontendBuild =>
        Map(
          "hash" -> build.hash,
          "asset_filename_list" -> build.assets.all
            .map(_.filename.toString)
            .mkString(", "),
        )
      case build: HostBuild =>
        Map(
          "platform" -> build.platform.toString,
          "pub_date" -> build.pubDate,
          "url"      -> build.uri.renderString,
          "notes"    -> build.notes.getOrElse("<none>"),
        )
    }

    Map(
      "branch"    -> build.branch.toString,
      "version"   -> build.version,
      "number"    -> build.number.toString,
      "is_revert" -> deploy.isRevert.toString,
    ) ++ buildProperties
  }

  override def publish(deploy: Deploy): F[Unit] = {
    val message = replacers(deploy).foldLeft(format) { (format, mapping) =>
      format.replace(s"$$${mapping._1}", mapping._2)
    }

    Sync[F].delay(println(message))
  }
}

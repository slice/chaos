package zone.slice.chaos
package publisher

import discord.Deploy

import cats.effect.Sync

class StdoutPublisher[F[_]: Sync](format: String) extends Publisher[F] {

  val replacers: Deploy => Map[String, String] = (deploy: Deploy) => {
    val build = deploy.build

    Map(
      "branch"       -> build.branch.toString,
      "build_number" -> build.buildNumber.toString,
      "hash"         -> build.hash,
      "is_revert"    -> deploy.isRevert.toString,
      "asset_filename_list" -> build.assets.all
        .map(_.filename.toString)
        .mkString(", "),
    )
  }

  override def publish(deploy: Deploy): F[Unit] = {
    val message = replacers(deploy).foldLeft(format) { (format, mapping) =>
      format.replace(s"$$${mapping._1}", mapping._2)
    }

    Sync[F].delay(println(message))
  }
}

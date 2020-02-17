package zone.slice.chaos
package publisher

import discord.Build

import cats.effect.Sync

class StdoutPublisher[F[_]: Sync](format: String) extends Publisher[F] {

  val replacers: Build => Map[String, String] = (build: Build) =>
    Map(
      "branch" -> build.branch.toString,
      "build_number" -> build.buildNumber.toString,
      "asset_filename_list" -> build.assets.all
        .map(_.filename.toString)
        .mkString(", ")
  )

  override def publish(build: Build): F[Unit] = {
    val message = replacers(build).foldLeft(format) { (format, mapping) =>
      format.replace(s"$$${mapping._1}", mapping._2)
    }

    Sync[F].delay(println(message))
  }
}

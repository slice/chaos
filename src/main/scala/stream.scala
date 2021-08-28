package zone.slice.chaos

import java.nio.file.{Path => NIOPath}
import fs2.{Stream, Pipe}
import fs2.io.file.{Files, Path, Flags}
import fs2.io.file.Flag
import cats.effect.MonadCancel

package object stream {

  /** "Continuously overwrite" a string stream into a file, truncating the file
    * before each write.
    */
  def continuouslyOverwrite[F[_]](
      path: NIOPath,
  )(implicit F: Files[F], MC: MonadCancel[F, _]): Pipe[F, String, Unit] = {
    val cursor =
      F.writeCursor(
        Path.fromNioPath(path),
        Flags(Flag.Create, Flag.Write, Flag.Truncate),
      )

    (in) =>
      in.flatMap(chunk =>
        Stream
          .resource(cursor)
          .flatMap(
            _.writeAll(Stream(chunk).through(fs2.text.utf8.encode)).void.stream,
          ),
      )
  }

}

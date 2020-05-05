package zone.slice.chaos

import java.nio.file._
import cats.effect.{Sync, Blocker, ContextShift}
import fs2.{Stream, Pipe}
import fs2.text.utf8Encode
import fs2.io.file.WriteCursor

package object stream {

  /** "Continuously overwrite" a string stream into a file, truncating the
    * file before each write.
    */
  def continuouslyOverwrite[F[_]: Sync: ContextShift](
      blocker: Blocker,
      path: Path,
  ): Pipe[F, String, Unit] = {

    val cursor =
      WriteCursor.fromPath(path, blocker, List(StandardOpenOption.CREATE))

    (in) =>
      in.flatMap(chunk =>
        Stream
          .resource(cursor)
          .flatMap(_.writeAll(Stream(chunk).through(utf8Encode)).void.stream),
      )
  }

}

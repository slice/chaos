package zone.slice.chaos

import cats.effect.MonadCancel
import fs2.{Stream, Pipe}
import fs2.io.file.{Files, Path, Flags, Flag}

def continuouslyOverwrite[F[_]](
    path: Path,
)(using F: Files[F])(using MonadCancel[F, ?]): Pipe[F, String, Nothing] =
  val cursor =
    F.writeCursor(path, Flags(Flag.Create, Flag.Write, Flag.Truncate))

  (in) =>
    in.flatMap(chunk =>
      Stream
        .resource(cursor)
        .flatMap(
          _.writeAll(Stream(chunk).through(fs2.text.utf8.encode)).void.stream,
        ),
    )

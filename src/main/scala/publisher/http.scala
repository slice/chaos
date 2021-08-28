package zone.slice.chaos
package publisher

import cats.effect.Sync
import org.http4s.client.dsl.Http4sClientDsl
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait HTTPPublisher[F[_]] extends Publisher[F] with Http4sClientDsl[F] {
  protected implicit def unsafeLogger(implicit ev: Sync[F]): Logger[F] =
    Slf4jLogger.getLogger[F]
}

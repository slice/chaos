package zone.slice.chaos
package publisher

import cats.effect.Sync
import org.http4s.client.dsl.Http4sClientDsl
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

trait HTTPPublisher[F[_]] extends Publisher[F] with Http4sClientDsl[F] {
  protected implicit def unsafeLogger(implicit ev: Sync[F]): Logger[F] =
    Slf4jLogger.getLogger[F]
}

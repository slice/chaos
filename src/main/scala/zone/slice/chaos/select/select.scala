package zone.slice.chaos
package select

/** A typeclass that encompasses the ability of selecting a named value from a
  * type. Names should always be assumed to be lowercase.
  */
trait Select[A] {
  def select(selector: String): Option[A]
}

object Select {
  def apply[A](implicit ev: Select[A]): Select[A] = ev

  def instance[A](f: String => Option[A]) = new Select[A] {
    def select(selector: String): Option[A] = f(selector)
  }

  def fromPartialFunction[A](f: PartialFunction[String, A]) =
    instance(f.lift)
}

package zone.slice.chaos
package select

final case class Selected[A](selector: String, value: A)

/** A typeclass that encompasses the ability of selecting a named value from a
  * type. Names should always be assumed to be lowercase.
  */
trait Select[A] {
  def all: Map[String, A]

  def aliases: Map[String, A] = Map.empty

  def star: Map[String, A] = all

  def select(key: String): Option[A] =
    all.get(key).orElse(aliases.get(key))

  def canonicalize(selector: String): Set[String] =
    selector match {
      case _ if selector.startsWith("{") && selector.endsWith("}") =>
        selector
          .substring(1, selector.length - 1)
          .split(',')
          .toSet
      case "*" =>
        star.keys.toSet
      case selector =>
        Set(selector)
    }

  def multiselect(selector: String): Set[Selected[A]] =
    canonicalize(selector).flatMap { canonSelector =>
      select(canonSelector).map { value =>
        Selected(canonSelector, value)
      }.toSet
    }
}

object Select {
  def apply[A](implicit ev: Select[A]): Select[A] = ev

  def instance[A](map: Map[String, A]) = new Select[A] {
    def all = map
  }
}

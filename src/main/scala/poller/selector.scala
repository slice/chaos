package zone.slice.chaos
package poller

/** An item that was selected, alongside a normalized selector to select it. */
case class Selected[A](item: A, normalizedSelector: String)

/** A type class for selecting keyed items from a map using a selector string.
  *
  * A map is used instead of a set because it allows for more straightforward
  * selecting when a direct key is specified, and it allows keys to be
  * reverse-selected (e.g. when doing `multiselect("*")`).
  *
  * The `multiselect` is the most useful method that you get out of this type
  * class. It lets you select multiple items from a selector string, which is
  * really just a string that refers to (possibly multiple) key(s), including
  * the multiselection syntax (`"{first,second,third,...}"`) and the star glob
  * syntax (`"*"`).
  */
trait Select[A] {

  /** A map of keyed items. */
  def all: Map[String, A]

  /** An additional map of key aliases. */
  def aliases: Map[String, String] = Map[String, String]()

  /* Selects an item directly from its key, also falling back to aliases. */
  def select(key: String): Option[Selected[A]] =
    all
      .get(key)
      .orElse(aliases.get(key).flatMap(all.get))
      .map(Selected(_, key))

  /** Selects a set of items from a selector string, allowing for
    * multiselection syntax and star globbing.
    */
  def multiselect(selector: String): Set[Selected[A]] =
    selector match {
      // {first,second,third,...}
      case _ if selector.startsWith("{") && selector.endsWith("}") =>
        selector
          .substring(1, selector.length - 1)
          .split(',')
          .toSet
          .flatMap(select)
      case "*" =>
        all
          .map({
            case (normalizedSelector, item) =>
              Selected(item, normalizedSelector)
          })
          .toSet
      case _ =>
        select(selector).toSet
    }
}

object Select {
  def apply[A](implicit ev: Select[A]): Select[A] = ev
}

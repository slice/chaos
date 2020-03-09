package zone.slice.chaos
package poller

import discord.Branch

/**
  * A type class for selecting things from a set according to a string.
  */
trait Select[A] {
  def all: Set[A]
  def select(selector: String): A
}

object Select {
  def apply[A](implicit ev: Select[A]): Select[A] = ev

  def instance[A](all0: Set[A])(select0: String => A): Select[A] =
    new Select[A] {
      val all                         = all0
      def select(selector: String): A = select0(selector)
    }

  implicit val selectBranch: Select[Branch] =
    Select.instance(Branch.all) {
      case "stable" | "s" => Branch.Stable
      case "ptb" | "p"    => Branch.PTB
      case "canary" | "c" => Branch.Canary
    }
}

case class Selector[V](variants: Set[V])

object Selector {
  val selectorSetRegex = """\{\w+(,\w+)*\}""".r

  def select[A](selector: String)(implicit ev: Select[A]): Selector[A] = {
    def go(portion: String): Set[A] =
      portion match {
        case selectorSetRegex(_*) =>
          portion
            .substring(1, portion.length - 1)
            .split(',')
            .to(Set)
            .flatMap(go)
        case "*" =>
          ev.all
        case _ =>
          Set(ev.select(portion))
      }

    Selector(go(selector))
  }
}

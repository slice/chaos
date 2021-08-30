package zone.slice.chaos.discord

import cats.syntax.all.*
import cats.Eq

enum Branch:
  case Stable
  case Ptb
  case Canary
  case Development

  def color: Int =
    this match
      case Stable      => 0x7289da
      case Ptb         => 0x99aab5
      case Canary      => 0xf1c40f
      case Development => 0x333333

  def humanName: String =
    if this == Ptb then "PTB" else this.toString

  def hasFrontend: Boolean =
    this != Development

  def subdomain: Option[String] =
    this match
      case Stable      => none
      case Ptb         => "ptb".some
      case Canary      => "canary".some
      case Development => none

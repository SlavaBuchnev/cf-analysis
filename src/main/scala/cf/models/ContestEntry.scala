package cf.models

import zio.Ref

/** Внутреннее представление контеста в кэше.
  *
  * Хранит текущее [[ContestState]] и timestamp последнего обновления
  * (`lastRefreshed`, unix-миллисекунды).
  */
case class ContestEntry(
    state: Ref[ContestState],
    lastRefreshed: Ref[Long],
)

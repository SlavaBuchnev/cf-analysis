package cf.models

import zio.Ref

case class ContestEntry(
    state: Ref[ContestState],
    lastRefreshed: Ref[Long],
)

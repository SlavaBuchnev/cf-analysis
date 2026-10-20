package cf.models

/** Накопленное состояние одного контеста.
  *
  * Хранится `Map[Long, CfSubmission]` по id посылки, а не `List`, чтобы:
  *   1. upsert'ить посылки при смене вердикта (TESTING → OK, WRONG_ANSWER → OK и т.п.);
  *   2. дедуплицировать посылки, если CF вернёт одну и ту же дважды (например, при пересечении страниц пагинации или
  *      при SWR-refresh);
  *   3. O(1) проверять «есть ли уже такая посылка» без обхода списка.
  */
final case class ContestState(submissions: Map[Long, CfSubmission]) {

  /** Максимальный id посылки в состоянии (0, если состояние пусто). */
  def lastId: Long = submissions.keys.maxOption.getOrElse(0L)

  /** Количество посылок в состоянии. */
  def size: Int = submissions.size

  /** Пусто ли состояние. */
  def isEmpty: Boolean = submissions.isEmpty

  /** Непусто ли состояние. */
  def nonEmpty: Boolean = submissions.nonEmpty

  /** Посылки, отсортированные по id по возрастанию. */
  def sorted: List[CfSubmission] = submissions.values.toList.sortBy(_.id)

  /** Объединить текущее состояние с входящим списком посылок (upsert по id). */
  def merge(incoming: List[CfSubmission]): ContestState =
    if (incoming.isEmpty) this
    else copy(submissions = submissions ++ incoming.iterator.map(s => s.id -> s))

  /** Количество посылок, добавленных по сравнению с `previous`. */
  def diffAdded(previous: ContestState): Int =
    submissions.keySet.diff(previous.submissions.keySet).size
}

object ContestState {

  val empty: ContestState = ContestState(Map.empty)

  /** Собрать состояние из полного списка посылок (используется при initial load). */
  def from(submissions: List[CfSubmission]): ContestState =
    ContestState(submissions.iterator.map(s => s.id -> s).toMap)
}

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap

import io.micrometer.core.instrument.Tags
import io.micrometer.prometheusmetrics.{PrometheusConfig, PrometheusMeterRegistry}

object MetricsCollector {

  val registry: PrometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

  private val gaugeValues = new ConcurrentHashMap[String, AtomicReference[Double]]()

  def updateGauge(name: String, tags: Tags, value: Double): Unit = {
    val key = s"$name${tags.toString}"
    val atomic = gaugeValues.computeIfAbsent(
      key,
      _ => {
        val ref = new AtomicReference[Double](value)
        registry.gauge(name, tags, ref, (r: AtomicReference[Double]) => r.get().doubleValue())
        ref
      },
    )
    atomic.set(value)
  }

  private def hasTimeConstraint(participantType: String): Boolean =
    participantType match {
      case "CONTESTANT" | "OUT_OF_COMPETITION" | "VIRTUAL" => true
      case _ => false
    }

  private def normalizedHandle(s: CfSubmission): String =
    s.author.members.headOption.map(_.handle.toLowerCase).getOrElse("unknown")

  def updateContestMetrics(
      contestId: Int,
      statuses: List[CfSubmission],
      targetHandles: List[String],
      groupCode: Option[String],
  ): Unit = {
    val group = groupCode.getOrElse("")
    val cid = contestId.toString

    val normalizedTargets = targetHandles.map(_.toLowerCase).toSet
    val filtered =
      if (normalizedTargets.isEmpty) statuses.filter(_.contestId == contestId)
      else
        statuses.filter { s =>
          s.contestId == contestId &&
          s.author.members.exists(m => normalizedTargets.contains(m.handle.toLowerCase))
        }

    filtered.groupBy(normalizedHandle).foreach { case (handle, subs) =>
      val base = Tags.of("handle", handle, "contestId", cid, "group", group)

      // ---------------------------------------------------------
      // 1. Посылки по (language, verdict)
      // ---------------------------------------------------------
      subs.groupBy(s => (s.programmingLanguage, s.verdict)).foreach { case ((lang, verdict), list) =>
        updateGauge(
          "cf_user_submissions",
          base.and("language", lang).and("verdict", verdict),
          list.size.toDouble,
        )
      }

      // ---------------------------------------------------------
      // 2. Разбор по задачам
      // ---------------------------------------------------------
      subs.groupBy(_.problem.index).foreach { case (pIndex, problemSubs) =>
        val pBase = base.and("problemIndex", pIndex)

        // 2.1 Посылки по вердикту (attempts / rejected / error_breakdown — всё здесь)
        problemSubs.groupBy(_.verdict).foreach { case (verdict, list) =>
          updateGauge("cf_user_problem_submissions", pBase.and("verdict", verdict), list.size.toDouble)
        }

        val sorted = problemSubs.sortBy(_.id)
        val firstOk = sorted.find(_.verdict == "OK")

        // 2.2 Флаги 0/1
        updateGauge("cf_user_problem_solved", pBase, if (firstOk.isDefined) 1.0 else 0.0)
        updateGauge(
          "cf_user_problem_oneshot",
          pBase,
          if (sorted.headOption.exists(_.verdict == "OK")) 1.0 else 0.0,
        )

        // 2.3 Числовые — только по посылкам с ограничением времени
        val timed = problemSubs.filter(s => hasTimeConstraint(s.author.participantType)).sortBy(_.id)
        timed.find(_.verdict == "OK").foreach { ok =>
          val solveTimeSec = ok.relativeTimeSeconds.filter(_ >= 0).getOrElse(0L)
          val rejectedBefore = timed.takeWhile(_.id < ok.id).count(_.verdict != "OK")

          updateGauge(
            "cf_user_problem_solve_time",
            pBase.and("language", ok.programmingLanguage),
            solveTimeSec.toDouble,
          )
          updateGauge(
            "cf_user_problem_penalty",
            pBase,
            solveTimeSec / 60.0 + 10.0 * rejectedBefore,
          )
          updateGauge("cf_user_problem_attempts_before_ac", pBase, rejectedBefore.toDouble)
        }
      }
    }
  }
}

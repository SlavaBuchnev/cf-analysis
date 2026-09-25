package cf.models

/** Фабрики тестовых данных для моделей cf.models. */
object Fixtures {

  def mkMember(handle: String = "user"): CfMember =
    CfMember(handle = handle)

  def mkParty(
      contestId: Option[Int] = Some(1),
      members: List[CfMember] = List(mkMember()),
      participantType: String = "CONTESTANT",
      teamId: Option[Int] = None,
      teamName: Option[String] = None,
      ghost: Boolean = false,
      startTimeSeconds: Option[Long] = Some(0L),
  ): CfParty =
    CfParty(
      contestId = contestId,
      members = members,
      participantType = participantType,
      teamId = teamId,
      teamName = teamName,
      ghost = ghost,
      startTimeSeconds = startTimeSeconds,
    )

  def mkProblem(
      contestId: Int = 1,
      index: String = "A",
      name: String = "Test problem",
      `type`: String = "PROGRAMMING",
      points: Option[Double] = Some(500.0),
      rating: Option[Int] = Some(1500),
      tags: List[String] = List("implementation"),
  ): CfProblem =
    CfProblem(
      contestId = contestId,
      index = index,
      name = name,
      `type` = `type`,
      points = points,
      rating = rating,
      tags = tags,
    )

  def submission(
      id: Long = 1L,
      contestId: Int = 1,
      creationTimeSeconds: Long = 0L,
      relativeTimeSeconds: Option[Long] = Some(0L),
      problem: Option[CfProblem] = None,
      author: Option[CfParty] = None,
      programmingLanguage: String = "lang",
      verdict: String = "OK",
      testset: String = "TESTS",
      passedTestCount: Int = 1,
      timeConsumedMillis: Int = 10,
      memoryConsumedBytes: Int = 1024,
  ): CfSubmission =
    CfSubmission(
      id = id,
      contestId = contestId,
      creationTimeSeconds = creationTimeSeconds,
      relativeTimeSeconds = relativeTimeSeconds,
      problem = problem.getOrElse(mkProblem(contestId = contestId)),
      author = author.getOrElse(mkParty(contestId = Some(contestId))),
      programmingLanguage = programmingLanguage,
      verdict = verdict,
      testset = testset,
      passedTestCount = passedTestCount,
      timeConsumedMillis = timeConsumedMillis,
      memoryConsumedBytes = memoryConsumedBytes,
    )

  def apiResponseOk(
      submissions: List[CfSubmission] = List(submission()),
  ): CfApiResponse =
    CfApiResponse(status = "OK", result = submissions, comment = None)

  def apiResponseFailed(
      comment: String = "unknown error",
  ): CfApiResponse =
    CfApiResponse(status = "FAILED", result = Nil, comment = Some(comment))
}

import zio.json.*

case class CfApiResponse(
    status: String,
    result: List[CfSubmission],
    comment: Option[String],
) derives JsonDecoder

case class CfProblem(
    contestId: Int,
    index: String,
    name: String,
    `type`: String,
    points: Option[Double],
    rating: Option[Int],
    tags: List[String],
) derives JsonDecoder,
      JsonEncoder

case class CfParty(
    contestId: Option[Int],
    members: List[CfMember],
    participantType: String,
    teamId: Option[Int],
    teamName: Option[String],
    ghost: Boolean,
    startTimeSeconds: Option[Long],
) derives JsonDecoder,
      JsonEncoder

case class CfMember(handle: String) derives JsonDecoder, JsonEncoder

case class CfSubmission(
    id: Long,
    contestId: Int,
    creationTimeSeconds: Long,
    relativeTimeSeconds: Option[Long],
    problem: CfProblem,
    author: CfParty,
    programmingLanguage: String,
    verdict: String,
    testset: String,
    passedTestCount: Int,
    timeConsumedMillis: Int,
    memoryConsumedBytes: Int,
) derives JsonDecoder,
      JsonEncoder

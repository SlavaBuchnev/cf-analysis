package cf.models

import zio.json.DecoderOps
import zio.test.{assertTrue, ZIOSpecDefault}

object CfModelsSpec extends ZIOSpecDefault {

  private val fullJson: String =
    """
      {
        "status": "OK",
        "result": [
          {
            "id": 123,
            "contestId": 1000,
            "creationTimeSeconds": 1700000000,
            "relativeTimeSeconds": 3600,
            "problem": {
              "contestId": 1000,
              "index": "A",
              "name": "Test Problem",
              "type": "PROGRAMMING",
              "points": 500.0,
              "rating": 800,
              "tags": ["math", "implementation"]
            },
            "author": {
              "contestId": 1000,
              "members": [{"handle": "Alice"}, {"handle": "Bob"}],
              "participantType": "CONTESTANT",
              "teamId": 42,
              "teamName": "Team X",
              "ghost": false,
              "startTimeSeconds": 1699990000
            },
            "programmingLanguage": "GNU G++17",
            "verdict": "OK",
            "testset": "tests",
            "passedTestCount": 10,
            "timeConsumedMillis": 15,
            "memoryConsumedBytes": 1024
          }
        ],
        "comment": "some comment"
      }
    """

  private val minimalJson: String =
    """
      {
        "status": "FAILED",
        "result": [],
        "comment": null
      }
    """

  def spec = suite("CfModelsSpec")(
    suite("CfApiResponse")(
      test("decodes all fields of full response") {
        val decoded = fullJson.fromJson[CfApiResponse]
        assertTrue(
          decoded.exists { resp =>
            resp.status == "OK" &&
            resp.comment.contains("some comment") &&
            resp.result.size == 1
          },
        )
      },
      test("decodes response with null comment") {
        val decoded = minimalJson.fromJson[CfApiResponse]
        assertTrue(
          decoded.exists { resp =>
            resp.status == "FAILED" &&
            resp.comment.isEmpty &&
            resp.result.isEmpty
          },
        )
      },
    ),
    suite("CfSubmission")(
      test("decodes all fields") {
        val decoded = fullJson.fromJson[CfApiResponse].map(_.result.head)
        assertTrue(
          decoded.exists { s =>
            s.id == 123L &&
            s.contestId == 1000 &&
            s.creationTimeSeconds == 1700000000L &&
            s.relativeTimeSeconds.contains(3600L) &&
            s.programmingLanguage == "GNU G++17" &&
            s.verdict == "OK" &&
            s.testset == "tests" &&
            s.passedTestCount == 10 &&
            s.timeConsumedMillis == 15 &&
            s.memoryConsumedBytes == 1024
          },
        )
      },
    ),
    suite("CfProblem")(
      test("decodes all fields") {
        val problem = fullJson.fromJson[CfApiResponse].map(_.result.head.problem)
        assertTrue(
          problem.exists { p =>
            p.contestId == 1000 &&
            p.index == "A" &&
            p.name == "Test Problem" &&
            p.`type` == "PROGRAMMING" &&
            p.points.contains(500.0) &&
            p.rating.contains(800) &&
            p.tags == List("math", "implementation")
          },
        )
      },
      test("decodes with optional points/rating absent") {
        val json =
          """
            {
              "contestId": 1, "index": "B", "name": "N",
              "type": "PROGRAMMING", "tags": []
            }
          """
        val decoded = json.fromJson[CfProblem]
        assertTrue(
          decoded.exists { p =>
            p.points.isEmpty &&
            p.rating.isEmpty &&
            p.tags.isEmpty
          },
        )
      },
    ),
    suite("CfParty")(
      test("decodes all fields with team") {
        val party = fullJson.fromJson[CfApiResponse].map(_.result.head.author)
        assertTrue(
          party.exists { p =>
            p.contestId.contains(1000) &&
            p.members.map(_.handle) == List("Alice", "Bob") &&
            p.participantType == "CONTESTANT" &&
            p.teamId.contains(42) &&
            p.teamName.contains("Team X") &&
            !p.ghost &&
            p.startTimeSeconds.contains(1699990000L)
          },
        )
      },
      test("decodes individual party without optional team fields") {
        val json =
          """
            {
              "members": [{"handle": "Solo"}],
              "participantType": "PRACTICE",
              "ghost": true
            }
          """
        val decoded = json.fromJson[CfParty]
        assertTrue(
          decoded.exists { p =>
            p.contestId.isEmpty &&
            p.teamId.isEmpty &&
            p.teamName.isEmpty &&
            p.startTimeSeconds.isEmpty &&
            p.ghost
          },
        )
      },
    ),
    suite("CfMember")(
      test("decodes handle") {
        val decoded = """{"handle": "Charlie"}""".fromJson[CfMember]
        assertTrue(
          decoded.exists(_.handle == "Charlie"),
        )
      },
    ),
  )
}

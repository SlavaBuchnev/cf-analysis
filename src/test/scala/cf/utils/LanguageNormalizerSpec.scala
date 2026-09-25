package cf.utils

import zio.test.{assertTrue, ZIOSpecDefault}

object LanguageNormalizerSpec extends ZIOSpecDefault { 
  private val cases: List[(String, String)] = List(
    "GNU G++17" -> "C++",
    "Clang++17" -> "C++",
    "PyPy 3" -> "Python",
    "Python 3.8" -> "Python",
    "Java 8" -> "Java",
    "Kotlin 1.4" -> "Kotlin",
    "Mono C#" -> "C#",
    "GNU C11" -> "C",
    "Node.js" -> "JavaScript",
    "Go" -> "Go",
    "Rust 1.60" -> "Rust",
    "Scala 2.13" -> "Scala",
    "Ruby 3" -> "Ruby",
    "PHP 8" -> "PHP",
    "Haskell" -> "Haskell",
    "Perl" -> "Perl",
    "OCaml" -> "OCaml",
    "Delphi" -> "Pascal",
    "D" -> "D",
    "Brainfuck" -> "Other",
  )

  def spec = suite("LanguageNormalizerSpec")(
    cases.map { case (input, expected) =>
      test(s"normalizes '$input' to '$expected'") {
        assertTrue(LanguageNormalizer.normalize(input) == expected)
      }
    },
  )
}

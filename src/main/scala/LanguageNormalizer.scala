object LanguageNormalizer {

  def normalize(language: String): String =
    language.trim match {
      // --- C++ ---
      case s if s.contains("C++") || s.startsWith("Clang++") => "C++"

      // --- Python ---
      case s if s.startsWith("PyPy") || s.startsWith("Python") || s.startsWith("CPython") => "Python"

      // --- Java ---
      case s if s.startsWith("Java") => "Java"

      // --- Kotlin ---
      case s if s.startsWith("Kotlin") => "Kotlin"

      // --- C# ---
      case s if s.startsWith("C#") || s.contains("Mono C#") => "C#"

      // --- C ---
      case s if Set("GNU C", "GNU C11", "C", "C11").contains(s) || (s.startsWith("Clang") && !s.contains("++")) => "C"

      // --- JavaScript ---
      case s if s.startsWith("Node.js") || s.startsWith("V8") || s.contains("JavaScript") => "JavaScript"

      // --- Go ---
      case s if s == "Go" || s.startsWith("Go ") => "Go"

      // --- Rust ---
      case s if s.startsWith("Rust") => "Rust"

      // --- Scala ---
      case s if s.startsWith("Scala") => "Scala"

      // --- Ruby ---
      case s if s.startsWith("Ruby") => "Ruby"

      // --- PHP ---
      case s if s.startsWith("PHP") => "PHP"

      // --- Haskell ---
      case s if s.startsWith("Haskell") => "Haskell"

      // --- Perl ---
      case s if s.startsWith("Perl") => "Perl"

      // --- OCaml ---
      case s if s.contains("OCaml") => "OCaml"

      // --- Pascal / Delphi ---
      case s if s.contains("Pascal") || s == "Delphi" => "Pascal"

      // --- D ---
      case s if s == "D" || s.startsWith("D ") => "D"

      // --- Прочее ---
      case _ => "Other"
    }
}

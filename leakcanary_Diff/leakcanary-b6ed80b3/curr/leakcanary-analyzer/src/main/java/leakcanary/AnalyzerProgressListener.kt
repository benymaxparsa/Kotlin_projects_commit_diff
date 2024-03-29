package leakcanary

interface AnalyzerProgressListener {

  // These steps should be defined in the order in which they occur.
  enum class Step {
    READING_HEAP_DUMP_FILE,
    PARSING_HEAP_DUMP,
    DEDUPLICATING_GC_ROOTS,
    FINDING_LEAKING_REF,
    FINDING_LEAKING_REFS,
    FINDING_SHORTEST_PATH,
    BUILDING_LEAK_TRACE,
    COMPUTING_DOMINATORS
  }

  fun onProgressUpdate(step: Step)

  companion object {
    val NONE = object : AnalyzerProgressListener {
      override fun onProgressUpdate(step: Step) {
      }
    }
  }
}
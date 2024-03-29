package leakcanary

import java.io.PrintWriter
import java.io.StringWriter

class HeapAnalysisException(cause: Throwable) : RuntimeException(cause) {

  override fun toString(): String {
    val stringWriter = StringWriter()
    printStackTrace(PrintWriter(stringWriter))
    return "\n$stringWriter\n"
  }
}
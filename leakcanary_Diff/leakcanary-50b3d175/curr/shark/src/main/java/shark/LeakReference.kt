package shark

import shark.LeakTraceElement.Type.ARRAY_ENTRY
import shark.LeakTraceElement.Type.INSTANCE_FIELD
import shark.LeakTraceElement.Type.LOCAL
import shark.LeakTraceElement.Type.STATIC_FIELD
import java.io.Serializable

/**
 * This class is kept to support backward compatible deserialization.
 */
internal class LeakReference : Serializable {

  private val type: LeakTraceElement.Type? = null
  private val name: String? = null

  fun fromV20(originObject: LeakTraceObject) = ReferencePathElement(
      originObject = originObject,
      referenceType = when (type!!) {
        INSTANCE_FIELD -> ReferencePathElement.ReferenceType.INSTANCE_FIELD
        STATIC_FIELD -> ReferencePathElement.ReferenceType.STATIC_FIELD
        LOCAL -> ReferencePathElement.ReferenceType.LOCAL
        ARRAY_ENTRY -> ReferencePathElement.ReferenceType.ARRAY_ENTRY
      },
      referenceName = name!!
  )

  companion object {
    private const val serialVersionUID: Long = 2028550902155599651
  }
}
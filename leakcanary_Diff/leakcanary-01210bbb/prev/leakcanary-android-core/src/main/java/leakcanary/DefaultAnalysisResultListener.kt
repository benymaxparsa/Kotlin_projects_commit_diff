package leakcanary

import android.app.Application
import com.squareup.leakcanary.core.R
import leakcanary.Exclusion.Status.WEAKLY_REACHABLE
import leakcanary.Exclusion.Status.WONT_FIX_LEAK
import leakcanary.internal.Notifications
import leakcanary.internal.NotificationType.LEAKCANARY_RESULT
import leakcanary.internal.activity.LeakActivity
import leakcanary.internal.activity.db.HeapAnalysisTable
import leakcanary.internal.activity.db.LeakingInstanceTable
import leakcanary.internal.activity.db.LeaksDbHelper
import leakcanary.internal.activity.screen.GroupListScreen
import leakcanary.internal.activity.screen.HeapAnalysisFailureScreen
import leakcanary.internal.activity.screen.HeapAnalysisListScreen
import leakcanary.internal.activity.screen.HeapAnalysisSuccessScreen
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DefaultAnalysisResultListener : (Application, HeapAnalysis) -> Unit {
  override fun invoke(
    application: Application,
    heapAnalysis: HeapAnalysis
  ) {

    // TODO better log that include leakcanary version, exclusions, etc.
    CanaryLog.d("%s", heapAnalysis)

    val movedHeapDump = renameHeapdump(heapAnalysis.heapDumpFile)

    val updatedHeapAnalysis = when (heapAnalysis) {
      is HeapAnalysisFailure -> heapAnalysis.copy(heapDumpFile = movedHeapDump)
      is HeapAnalysisSuccess -> heapAnalysis.copy(heapDumpFile = movedHeapDump)
    }

    val (id, groupProjections) = LeaksDbHelper(application)
        .writableDatabase.use { db ->
      val id = HeapAnalysisTable.insert(db, updatedHeapAnalysis)
      id to LeakingInstanceTable.retrieveAllByHeapAnalysisId(db, id)
    }

    val (contentTitle, screenToShow) = when (heapAnalysis) {
      is HeapAnalysisFailure -> application.getString(
          R.string.leak_canary_analysis_failed
      ) to HeapAnalysisFailureScreen(id)
      is HeapAnalysisSuccess -> {
        var leakCount = 0
        var newLeakCount = 0
        var knownLeakCount = 0
        var wontFixLeakCount = 0

        for ((_, projection) in groupProjections) {
          if (projection.exclusionStatus != WEAKLY_REACHABLE) {
            leakCount += projection.leakCount
            when {
              projection.exclusionStatus == WONT_FIX_LEAK -> wontFixLeakCount += projection.leakCount
              projection.isNew -> newLeakCount += projection.leakCount
              else -> knownLeakCount += projection.leakCount
            }
          }
        }

        application.getString(
            R.string.leak_canary_analysis_success_notification, leakCount, newLeakCount,
            knownLeakCount, wontFixLeakCount
        ) to HeapAnalysisSuccessScreen(id)
      }
    }

    val pendingIntent = LeakActivity.createPendingIntent(
        application, arrayListOf(GroupListScreen(), HeapAnalysisListScreen(), screenToShow)
    )

    val contentText = application.getString(R.string.leak_canary_notification_message)

    Notifications.showNotification(
        application, contentTitle, contentText, pendingIntent,
        R.id.leak_canary_notification_analysis_result,
        LEAKCANARY_RESULT
    )
  }

  private fun renameHeapdump(heapDumpFile: File): File {
    val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS'.hprof'", Locale.US).format(Date())

    val newFile = File(heapDumpFile.parent, fileName)
    val renamed = heapDumpFile.renameTo(newFile)
    if (!renamed) {
      CanaryLog.d(
          "Could not rename heap dump file %s to %s", heapDumpFile.path, newFile.path
      )
    }
    return newFile
  }
}
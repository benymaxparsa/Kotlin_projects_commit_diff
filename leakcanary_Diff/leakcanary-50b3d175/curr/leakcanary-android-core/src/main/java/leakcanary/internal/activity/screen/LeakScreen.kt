package leakcanary.internal.activity.screen

import android.text.Html
import android.text.SpannableStringBuilder
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import com.squareup.leakcanary.core.R
import leakcanary.internal.DisplayLeakAdapter
import leakcanary.internal.SquigglySpan
import leakcanary.internal.activity.db.HeapAnalysisTable
import leakcanary.internal.activity.db.LeakTable
import leakcanary.internal.activity.db.LeakTable.LeakProjection
import leakcanary.internal.activity.db.executeOnDb
import leakcanary.internal.activity.share
import leakcanary.internal.activity.shareHeapDump
import leakcanary.internal.activity.shareToStackOverflow
import leakcanary.internal.activity.ui.SimpleListAdapter
import leakcanary.internal.activity.ui.TimeFormatter
import leakcanary.internal.activity.ui.UiUtils.replaceUrlSpanWithAction
import leakcanary.internal.navigation.Screen
import leakcanary.internal.navigation.activity
import leakcanary.internal.navigation.goTo
import leakcanary.internal.navigation.inflate
import shark.HeapAnalysisSuccess
import shark.LeakTrace
import shark.LibraryLeak

internal class LeakScreen(
  private val leakSignature: String,
  private val selectedHeapAnalysisId: Long? = null
) : Screen() {
  override fun createView(container: ViewGroup) =
    container.inflate(R.layout.leak_canary_leak_screen).apply {
      activity.title = resources.getString(R.string.leak_canary_loading_title)
      executeOnDb {
        val leak = LeakTable.retrieveLeakBySignature(db, leakSignature)

        if (leak == null) {
          updateUi {
            activity.title = resources.getString(R.string.leak_canary_leak_not_found)
          }
        } else {
          val selectedLeakIndex =
            if (selectedHeapAnalysisId == null) 0 else leak.leakTraces.indexOfFirst { it.heapAnalysisId == selectedHeapAnalysisId }

          val heapAnalysisId = leak.leakTraces[selectedLeakIndex].heapAnalysisId
          val selectedHeapAnalysis = HeapAnalysisTable.retrieve<HeapAnalysisSuccess>(db, heapAnalysisId)!!

          updateUi {
            onLeaksRetrieved(leak, selectedLeakIndex, selectedHeapAnalysis)
          }
          LeakTable.markAsRead(db, leakSignature)
        }
      }
    }

  private fun View.onLeaksRetrieved(
    leak: LeakProjection,
    selectedLeakTraceIndex: Int,
    selectedHeapAnalysis: HeapAnalysisSuccess
  ) {
    val isLibraryLeak = leak.isLibraryLeak
    val isNew = leak.isNew
    val newChipView = findViewById<TextView>(R.id.leak_canary_chip_new)
    val libraryLeakChipView = findViewById<TextView>(R.id.leak_canary_chip_library_leak)
    newChipView.visibility = if (isNew) View.VISIBLE else View.GONE
    libraryLeakChipView.visibility = if (isLibraryLeak) View.VISIBLE else View.GONE

    activity.title = String.format(
        resources.getQuantityText(
            R.plurals.leak_canary_group_screen_title, leak.leakTraces.size
        ).toString(), leak.leakTraces.size, leak.shortDescription
    )

    val spinner = findViewById<Spinner>(R.id.leak_canary_spinner)

    spinner.adapter =
      SimpleListAdapter(R.layout.leak_canary_simple_row, leak.leakTraces) { view, position ->
        val titleView = view.findViewById<TextView>(R.id.leak_canary_row_text)
        val timeView = view.findViewById<TextView>(R.id.leak_canary_row_small_text)
        titleView.text =
          view.resources.getString(
              R.string.leak_canary_class_has_leaked, leak.leakTraces[position].classSimpleName
          )
        timeView.text =
          TimeFormatter.formatTimestamp(view.context, leak.leakTraces[position].createdAtTimeMillis)
      }

    var lastSelectedLeakTraceIndex = selectedLeakTraceIndex
    var lastSelectedHeapAnalysis = selectedHeapAnalysis

    spinner.onItemSelectedListener = object : OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>?) {
      }

      override fun onItemSelected(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
      ) {
        val selectedLeakTrace = leak.leakTraces[position]

        val selectedHeapAnalysisId = selectedLeakTrace.heapAnalysisId
        val lastSelectedHeapAnalysisId = leak.leakTraces[lastSelectedLeakTraceIndex].heapAnalysisId

        if (selectedHeapAnalysisId != lastSelectedHeapAnalysisId) {
          executeOnDb {
            val newSelectedHeapAnalysis = HeapAnalysisTable.retrieve<HeapAnalysisSuccess>(db, selectedHeapAnalysisId)!!
            updateUi {
              lastSelectedLeakTraceIndex = position
              lastSelectedHeapAnalysis = newSelectedHeapAnalysis
              onLeakTraceSelected(newSelectedHeapAnalysis, selectedHeapAnalysisId, selectedLeakTrace.leakTraceIndex)
            }
          }
        } else {
          lastSelectedLeakTraceIndex = position
          onLeakTraceSelected(lastSelectedHeapAnalysis, selectedHeapAnalysisId, selectedLeakTrace.leakTraceIndex)
        }
      }
    }
    spinner.setSelection(selectedLeakTraceIndex)
  }


private fun View.onLeakTraceSelected(analysis: HeapAnalysisSuccess, heapAnalysisId: Long, leakTraceIndex: Int) {
  val selectedLeak = analysis.allLeaks.first { it.signature == leakSignature }
  val leakTrace = selectedLeak.leakTraces[leakTraceIndex]

    val listView = findViewById<ListView>(R.id.leak_canary_list)
    listView.alpha = 0f
    listView.animate()
        .alpha(1f)

    val titleText = """
      Open <a href="open_analysis">Heap Dump</a><br><br>
      Share leak trace <a href="share">as text</a> or on <a href="share_stack_overflow">Stack Overflow</a><br><br>
      Share <a href="share_hprof">Heap Dump file</a><br><br>
      References <b><u>underlined</u></b> are the likely causes of the leak.
      Learn more at <a href="https://squ.re/leaks">https://squ.re/leaks</a>
    """.trimIndent() + if (selectedLeak is LibraryLeak) "<br><br>" +
        "A <font color='#FFCC32'>Library Leak</font> is a leak coming from the Android Framework or Google libraries.<br><br>" +
        "<b>Leak pattern</b>: ${selectedLeak.pattern}<br><br>" +
        "<b>Description</b>: ${selectedLeak.description}" else ""

    val title = Html.fromHtml(titleText) as SpannableStringBuilder
    SquigglySpan.replaceUnderlineSpans(title, context)

    replaceUrlSpanWithAction(title) { urlSpan ->
      when (urlSpan) {
        "share" -> {
          {
            share(leakToString(leakTrace, analysis))
          }
        }
        "share_stack_overflow" -> {
          {
            shareToStackOverflow(leakToString(leakTrace, analysis))
          }
        }
        "open_analysis" -> {
          {
            goTo(HeapDumpScreen(heapAnalysisId))
          }
        }
        "share_hprof" -> {
          {
            shareHeapDump(analysis.heapDumpFile)
          }
        }
        else -> null
      }
    }

    val adapter = DisplayLeakAdapter(context, leakTrace, title)
    listView.adapter = adapter
  }

  private fun leakToString(
    leakTrace: LeakTrace,
    analysis: HeapAnalysisSuccess
  ) = """$leakTrace

METADATA

${if (analysis.metadata.isNotEmpty()) analysis.metadata.map { "${it.key}: ${it.value}" }.joinToString(
      "\n"
  ) else ""}
Analysis duration: ${analysis.analysisDurationMillis} ms"""
}
package com.phincall.webrtc.ui

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phincall.webrtc.PhinCallApplication
import com.phincall.webrtc.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Displays live Audio and Video statistics from readAudioDetails / readVideoDetails
 * every 3 seconds, matching the Avaya Reference Client Call Statistics feature.
 */
class CallStatsDialog : DialogFragment() {

    private var statsTextView: TextView? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val textView = TextView(requireContext()).apply {
            text = getString(R.string.stats_loading)
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(ContextCompat.getColor(context, R.color.ink_primary))
            setPadding(pad, pad, pad, pad)
        }
        statsTextView = textView

        val container = ScrollView(requireContext()).apply {
            addView(textView)
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.stats_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.dtmf_close, null)
            .create().apply {
                window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
    }

    override fun onStart() {
        super.onStart()
        val coordinator = (requireActivity().application as PhinCallApplication).interactionCoordinator
        lifecycleScope.launch {
            while (isActive) {
                coordinator.readMediaStatistics { summary ->
                    activity?.runOnUiThread {
                        statsTextView?.text = summary
                    }
                }
                delay(3000L)
            }
        }
    }

    override fun onDestroyView() {
        statsTextView = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "CallStatsDialog"
    }
}

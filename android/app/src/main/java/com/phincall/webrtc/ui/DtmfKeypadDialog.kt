package com.phincall.webrtc.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.GridLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phincall.webrtc.R
import com.phincall.webrtc.databinding.DialogDtmfKeypadBinding

class DtmfKeypadDialog(
    private val onDigitPressed: (String) -> Unit
) : DialogFragment() {

    private var _binding: DialogDtmfKeypadBinding? = null
    private val binding get() = _binding!!
    private val enteredDigits = StringBuilder()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogDtmfKeypadBinding.inflate(LayoutInflater.from(requireContext()))

        // Avaya Oceana supports DTMF digits 0-9, *, and # (Guide 4.0.10, p. 78).
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        val buttonSizePx = (64 * resources.displayMetrics.density).toInt()
        val marginPx = (6 * resources.displayMetrics.density).toInt()

        keys.forEach { digit ->
            val button = MaterialButton(requireContext()).apply {
                text = digit
                textSize = 20f
                setTextColor(ContextCompat.getColor(context, R.color.ink_on_dark))
                setBackgroundColor(ContextCompat.getColor(context, R.color.dock_button_idle))
                cornerRadius = buttonSizePx / 2
                contentDescription = "DTMF tone $digit"
                layoutParams = GridLayout.LayoutParams().apply {
                    width = buttonSizePx
                    height = buttonSizePx
                    setMargins(marginPx, marginPx, marginPx, marginPx)
                }
                setOnClickListener {
                    enteredDigits.append(digit)
                    binding.textDtmfEcho.text = enteredDigits.toString()
                    onDigitPressed(digit)
                }
            }
            binding.gridDtmfKeys.addView(button)
        }

        binding.buttonCloseDtmf.setOnClickListener { dismiss() }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "DtmfKeypadDialog"
    }
}

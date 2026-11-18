package com.phincall.webrtc.ui

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.phincall.webrtc.PhinCallApplication
import com.phincall.webrtc.R
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.config.InputValidator
import com.phincall.webrtc.databinding.DialogDialpadSheetBinding

/**
 * Bottom sheet telephone keypad allowing users to dial any destination number
 * and initiate an Audio or Video interaction immediately.
 */
class DialpadBottomSheetDialog : BottomSheetDialogFragment() {

    interface DialpadHost {
        fun onDialpadCallRequested(destinationNumber: String, mode: CallMediaMode)
    }

    private var _binding: DialogDialpadSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDialpadSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as PhinCallApplication

        WindowInsetsHelper.applyBottomSafeArea(binding.dialpadSheetRoot)
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        // Prevent the system soft keyboard from covering the custom tactile keypad.
        binding.editDialNumber.showSoftInputOnFocus = false

        val initialNumber = arguments?.getString(ARG_INITIAL_NUMBER).orEmpty()
        if (initialNumber.isNotBlank()) {
            binding.editDialNumber.setText(initialNumber)
            binding.editDialNumber.setSelection(initialNumber.length)
        }

        renderPlatformHeader(app.runtimeConfigManager.currentPlatform)

        binding.buttonDialpadSwitchPlatform.setOnClickListener {
            val next = if (app.runtimeConfigManager.currentPlatform == PlatformType.OCEANA) {
                PlatformType.ELITE
            } else {
                PlatformType.OCEANA
            }
            app.runtimeConfigManager.switchPlatform(next)
            renderPlatformHeader(next)
            binding.textDialpadValidation.visibility = View.GONE
        }

        bindKeypadButtons()

        binding.buttonBackspace.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            deleteAtCursor()
        }
        binding.buttonBackspace.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            binding.editDialNumber.text?.clear()
            binding.textDialpadValidation.visibility = View.GONE
            true
        }

        binding.buttonDialAudio.setOnClickListener {
            dispatchDialAction(app, CallMediaMode.AUDIO)
        }
        binding.buttonDialVideo.setOnClickListener {
            dispatchDialAction(app, CallMediaMode.VIDEO)
        }
    }

    private fun renderPlatformHeader(platformType: PlatformType) {
        val label = if (platformType == PlatformType.ELITE) {
            getString(R.string.platform_elite)
        } else {
            getString(R.string.platform_oceana)
        }
        binding.textDialpadPlatform.text = "Routing via $label"
    }

    private fun bindKeypadButtons() {
        val keyPairs = listOf(
            binding.dialKey1 to "1",
            binding.dialKey2 to "2",
            binding.dialKey3 to "3",
            binding.dialKey4 to "4",
            binding.dialKey5 to "5",
            binding.dialKey6 to "6",
            binding.dialKey7 to "7",
            binding.dialKey8 to "8",
            binding.dialKey9 to "9",
            binding.dialKeyStar to "*",
            binding.dialKey0 to "0",
            binding.dialKeyHash to "#"
        )

        keyPairs.forEach { (keyView, digit) ->
            keyView.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                insertDigit(digit)
            }
        }

        binding.dialKey0.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            insertDigit("+")
            true
        }
    }

    private fun insertDigit(value: String) {
        val editable = binding.editDialNumber.text ?: return
        if (editable.length >= 64) return
        val start = binding.editDialNumber.selectionStart.coerceAtLeast(0)
        val end = binding.editDialNumber.selectionEnd.coerceAtLeast(0)
        val min = minOf(start, end)
        val max = maxOf(start, end)
        editable.replace(min, max, value)
        binding.textDialpadValidation.visibility = View.GONE
    }

    private fun deleteAtCursor() {
        val editable = binding.editDialNumber.text ?: return
        if (editable.isEmpty()) return
        val start = binding.editDialNumber.selectionStart.coerceAtLeast(0)
        val end = binding.editDialNumber.selectionEnd.coerceAtLeast(0)
        if (start != end) {
            editable.delete(minOf(start, end), maxOf(start, end))
        } else if (start > 0) {
            editable.delete(start - 1, start)
        } else {
            editable.delete(editable.length - 1, editable.length)
        }
        binding.textDialpadValidation.visibility = View.GONE
    }

    private fun dispatchDialAction(app: PhinCallApplication, mode: CallMediaMode) {
        val number = binding.editDialNumber.text?.toString()?.trim().orEmpty()
        val platform = app.runtimeConfigManager.currentPlatform

        if (platform == PlatformType.ELITE && number.isBlank()) {
            binding.textDialpadValidation.visibility = View.VISIBLE
            binding.textDialpadValidation.text = "Enter a destination number for Avaya Aura Elite."
            return
        }
        if (!InputValidator.isValidSipUser(number)) {
            binding.textDialpadValidation.visibility = View.VISIBLE
            binding.textDialpadValidation.text = "Destination contains disallowed SIP characters."
            return
        }

        val host = activity as? DialpadHost
        dismiss()
        host?.onDialpadCallRequested(number, mode)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "DialpadBottomSheetDialog"
        private const val ARG_INITIAL_NUMBER = "initial_number"

        fun newInstance(initialNumber: String = ""): DialpadBottomSheetDialog {
            return DialpadBottomSheetDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL_NUMBER, initialNumber)
                }
            }
        }
    }
}

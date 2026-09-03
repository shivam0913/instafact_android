package com.instafact.app.ui.report

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.instafact.app.BuildConfig
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.databinding.DialogReportIssueBinding
import kotlinx.coroutines.launch

/**
 * "Report an issue" from Profile: pick what went wrong, optionally describe it, send.
 *
 * The keys are what the server stores; the labels are only what the user reads, so the
 * wording can be reworded later without orphaning rows already in the table.
 */
object ReportIssueDialog {

    private val CATEGORIES = listOf(
        "wrong_result" to R.string.report_issue_cat_wrong_result,
        "crash" to R.string.report_issue_cat_crash,
        "link_not_working" to R.string.report_issue_cat_link,
        "notifications" to R.string.report_issue_cat_notifications,
        "login" to R.string.report_issue_cat_login,
        "other" to R.string.report_issue_cat_other,
    )

    fun show(context: Context, owner: LifecycleOwner) {
        val binding = DialogReportIssueBinding.inflate(LayoutInflater.from(context))

        CATEGORIES.forEach { (key, labelRes) ->
            binding.issueChipGroup.addView(
                Chip(context).apply {
                    text = context.getString(labelRes)
                    tag = key
                    isCheckable = true
                    isCheckedIconVisible = true
                },
            )
        }

        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Instafact_Dialog)
            .setView(binding.root)
            .create()

        binding.issueCancelButton.setOnClickListener { dialog.dismiss() }

        binding.issueSubmitButton.setOnClickListener {
            val category = binding.issueChipGroup.selectedKey()
            if (category == null) {
                binding.issueErrorTextView.isVisible = true
                binding.issueErrorTextView.text = context.getString(R.string.report_issue_pick_one)
                return@setOnClickListener
            }
            binding.issueErrorTextView.isVisible = false
            binding.issueSubmitButton.isEnabled = false
            binding.issueSubmitButton.text = context.getString(R.string.report_issue_sending)

            val application = context.applicationContext as? InstafactApplication
                ?: return@setOnClickListener dialog.dismiss()

            owner.lifecycleScope.launch {
                application.appContainer.submissionRepository.reportIssue(
                    category = category,
                    message = binding.issueDetailsEditText.text?.toString(),
                    appVersion = BuildConfig.VERSION_NAME,
                    // Context that turns "it doesn't work" into something reproducible.
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                )
                    .onSuccess {
                        dialog.dismiss()
                        Toast.makeText(
                            context,
                            context.getString(R.string.report_issue_thanks),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    .onFailure { error ->
                        // Left open with the text intact. The description is the user's
                        // work, and closing on failure would silently throw it away.
                        binding.issueSubmitButton.isEnabled = true
                        binding.issueSubmitButton.text =
                            context.getString(R.string.report_issue_submit)
                        binding.issueErrorTextView.isVisible = true
                        binding.issueErrorTextView.text = error.message
                    }
            }
        }

        // Material 1.12 resolves the dialog background to a tinted surface; force our own.
        dialog.window?.setBackgroundDrawable(
            ContextCompat.getDrawable(context, R.drawable.bg_dialog_surface),
        )
        dialog.show()
    }

    private fun ChipGroup.selectedKey(): String? =
        (0 until childCount)
            .mapNotNull { getChildAt(it) as? Chip }
            .firstOrNull { it.isChecked }
            ?.tag as? String
}

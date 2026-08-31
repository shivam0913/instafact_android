package com.instafact.app.ui.rating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatDialog
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.instafact.app.BuildConfig
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.databinding.DialogRateAppBinding
import com.instafact.app.databinding.DialogRatingFeedbackBinding
import com.instafact.app.utils.Analytics
import com.instafact.app.utils.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The in-app rating flow.
 *
 * Happy raters (4-5) go to the Play Store; unhappy ones (1-3) get an in-app form
 * instead, so a bad experience turns into feedback we can act on rather than a public
 * one-star review. This split is standard practice, but note it must never be used to
 * *filter* reviews - anyone can still rate on Play directly, and the Play Store listing
 * is never hidden from a low rater who wants it.
 */
object RatingPrompt {

    /** Paired value + analytics key, so the reason strings survive copy edits. */
    private val REASONS = listOf(
        "ui" to R.string.rating_feedback_reason_ui,
        "accuracy" to R.string.rating_feedback_reason_accuracy,
        "speed" to R.string.rating_feedback_reason_slow,
        "platforms" to R.string.rating_feedback_reason_platforms,
        "notifications" to R.string.rating_feedback_reason_notifications,
        "other" to R.string.rating_feedback_reason_other,
    )

    /**
     * Shows the prompt only if it has never been completed or dismissed.
     * Returns whether it was actually shown, so callers can avoid stacking dialogs.
     */
    fun showIfEligible(
        context: Context,
        preferenceManager: PreferenceManager,
        trigger: String,
    ): Boolean {
        if (preferenceManager.hasCompletedRatingPrompt()) return false
        show(context, preferenceManager, trigger)
        return true
    }

    /** Always shows, for the explicit "Rate us" entry in the profile. */
    fun show(
        context: Context,
        preferenceManager: PreferenceManager,
        trigger: String,
    ) {
        val binding = DialogRateAppBinding.inflate(android.view.LayoutInflater.from(context))
        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Instafact_Dialog)
            .setView(binding.root)
            .create()

        var selectedRating = 0
        val stars = listOf(
            binding.star1,
            binding.star2,
            binding.star3,
            binding.star4,
            binding.star5,
        )

        fun paintStars(rating: Int) {
            stars.forEachIndexed { index, star ->
                star.setImageResource(
                    if (index < rating) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
                )
            }
            // Submit stays inert until a rating exists, so it cannot post a zero.
            binding.ratingSubmitButton.alpha = if (rating > 0) 1f else 0.4f
            binding.ratingSubmitButton.isEnabled = rating > 0
        }

        stars.forEachIndexed { index, star ->
            val value = index + 1
            star.contentDescription = context.getString(R.string.rating_star_content_description, value)
            star.setOnClickListener {
                selectedRating = value
                paintStars(value)
            }
        }
        paintStars(0)

        binding.ratingNotNowButton.setOnClickListener {
            Analytics.logRatingDismissed(trigger)
            dialog.dismiss()
        }

        binding.ratingSubmitButton.setOnClickListener {
            if (selectedRating <= 0) return@setOnClickListener
            Analytics.logRatingSubmitted(selectedRating, trigger)
            // Completed either way: a rater should not be asked again on the next result.
            preferenceManager.setRatingPromptCompleted()
            dialog.dismiss()
            if (selectedRating >= 4) {
                openPlayStore(context)
            } else {
                showFeedbackDialog(context, selectedRating, trigger)
            }
        }

        dialog.applyBrandBackground(context)
        dialog.show()
        Analytics.logRatingPromptShown(trigger)
    }

    private fun showFeedbackDialog(context: Context, rating: Int, trigger: String) {
        val binding = DialogRatingFeedbackBinding.inflate(android.view.LayoutInflater.from(context))
        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Instafact_Dialog)
            .setView(binding.root)
            .create()

        REASONS.forEach { (key, labelRes) ->
            binding.reasonChipGroup.addView(
                Chip(context).apply {
                    text = context.getString(labelRes)
                    isCheckable = true
                    isCheckedIconVisible = true
                    tag = key
                },
            )
        }

        binding.feedbackCancelButton.setOnClickListener { dialog.dismiss() }
        binding.feedbackSendButton.setOnClickListener {
            val reasons = binding.reasonChipGroup.checkedReasonKeys()
            val comment = binding.feedbackCommentEditText.text?.toString()?.trim().orEmpty()
            Analytics.logRatingFeedback(
                rating = rating,
                reasons = reasons,
                comment = comment,
                trigger = trigger,
            )
            sendFeedbackToBackend(context, rating, reasons, comment, trigger)
            dialog.dismiss()
            // Acknowledged immediately: the send is fire-and-forget, and making someone
            // wait on a network round trip to be thanked would be worse than a lost row.
            Toast.makeText(context, R.string.rating_feedback_thanks, Toast.LENGTH_SHORT).show()
        }

        dialog.applyBrandBackground(context)
        dialog.show()
    }

    /**
     * Posts the rating to the backend, which is the only place the full comment is kept
     * (the analytics copy is truncated at 100 characters).
     *
     * Runs on the application scope, not the dialog's: the dialog is dismissed the
     * instant this is called, and a scope tied to it would cancel the request mid-flight.
     * Failures are swallowed - a lost feedback row must never surface as an error toast
     * to someone who just told us they are unhappy.
     */
    private fun sendFeedbackToBackend(
        context: Context,
        rating: Int,
        reasons: List<String>,
        comment: String,
        trigger: String,
    ) {
        val application = context.applicationContext as? InstafactApplication ?: return
        CoroutineScope(Dispatchers.IO).launch {
            application.appContainer.submissionRepository.submitAppFeedback(
                rating = rating,
                reasons = reasons,
                comment = comment,
                trigger = trigger,
                appVersion = BuildConfig.VERSION_NAME,
            ).onFailure { Log.w(TAG, "Could not send app feedback to the backend.", it) }
        }
    }

    private fun ChipGroup.checkedReasonKeys(): List<String> {
        return (0 until childCount)
            .mapNotNull { index -> getChildAt(index) as? Chip }
            .filter { it.isChecked }
            .mapNotNull { it.tag as? String }
    }

    /** Market URI first; the https listing is the fallback when Play is not installed. */
    private fun openPlayStore(context: Context) {
        val packageName = context.packageName
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        )
        runCatching { context.startActivity(marketIntent) }
            .recoverCatching { context.startActivity(webIntent) }
            .onFailure {
                Toast.makeText(context, R.string.play_store_unavailable, Toast.LENGTH_SHORT).show()
            }
    }

    // Material 1.12 resolves the dialog background to a tinted surface; force our own.
    private fun AppCompatDialog.applyBrandBackground(context: Context) {
        window?.setBackgroundDrawable(
            ContextCompat.getDrawable(context, R.drawable.bg_dialog_surface),
        )
    }

    private const val TAG = "RatingPrompt"

    const val TRIGGER_FIRST_RESULT = "first_result"
    const val TRIGGER_PROFILE = "profile"
}

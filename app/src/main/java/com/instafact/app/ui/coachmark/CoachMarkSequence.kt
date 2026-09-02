package com.instafact.app.ui.coachmark

import android.app.Activity
import android.graphics.Rect
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import com.instafact.app.R
import com.instafact.app.databinding.ViewCoachMarkBinding

/**
 * One step of the tour: the view to highlight and what to say about it.
 *
 * [targetProvider] is a lambda rather than a View because a step's target may not exist
 * yet when the sequence is built - the Home feed is a fragment that inflates
 * asynchronously, so its paste card resolves only once that has happened.
 */
data class CoachStep(
    val targetProvider: () -> View?,
    val titleRes: Int,
    val bodyRes: Int,
    val paddingPx: Int = 12,
)

/**
 * A first-run spotlight tour: dims the screen, highlights one control at a time and
 * explains it, with Skip available throughout.
 *
 * Added to the Activity's content view rather than a dialog window, so the spotlight can
 * be positioned against the real on-screen coordinates of the views it points at.
 */
class CoachMarkSequence private constructor(
    private val activity: Activity,
    private val steps: List<CoachStep>,
    private val onFinished: (completed: Boolean) -> Unit,
) {

    private val root: ViewGroup = activity.findViewById(android.R.id.content)
    private val spotlight = SpotlightView(activity)
    private val binding = ViewCoachMarkBinding.inflate(LayoutInflater.from(activity), root, false)
    private var index = 0
    private var dismissed = false

    private fun start() {
        root.addView(
            spotlight,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(binding.root)

        // Swallow taps on the scrim so the app underneath cannot be operated mid-tour.
        spotlight.setOnClickListener { }

        binding.coachSkipButton.setOnClickListener { dismiss(completed = false) }
        binding.coachNextButton.setOnClickListener {
            if (index >= steps.lastIndex) dismiss(completed = true) else showStep(index + 1)
        }

        binding.root.doOnLayout { showStep(0, animate = false) }
    }

    private fun showStep(stepIndex: Int, animate: Boolean = true) {
        if (dismissed) return
        index = stepIndex
        val step = steps[stepIndex]

        binding.coachStepTextView.text =
            activity.getString(R.string.coach_step_counter, stepIndex + 1, steps.size)
        binding.coachTitleTextView.setText(step.titleRes)
        binding.coachBodyTextView.setText(step.bodyRes)
        binding.coachNextButton.setText(
            if (stepIndex == steps.lastIndex) R.string.coach_done else R.string.next,
        )
        // Skip is pointless on the last step, where Next already means "done".
        binding.coachSkipButton.isVisible = stepIndex < steps.lastIndex

        val target = step.targetProvider()
        if (target == null || !target.isShown) {
            // A step whose target never appeared would trap the user behind an opaque
            // scrim with nothing highlighted, so skip past it instead.
            if (stepIndex >= steps.lastIndex) dismiss(completed = true) else showStep(stepIndex + 1, animate)
            return
        }

        val rect = target.boundsInRoot(step.paddingPx)
        spotlight.moveTo(rect, animate)
        positionCaption(rect)
    }

    /** Puts the caption below the spotlight, or above it when there is no room below. */
    private fun positionCaption(rect: RectF) {
        val caption = binding.coachCaptionContainer
        caption.doOnLayout {
            val gap = GAP_PX
            val belowTop = rect.bottom + gap
            val fitsBelow = belowTop + caption.height + gap <= root.height
            caption.translationY = if (fitsBelow) {
                belowTop
            } else {
                (rect.top - caption.height - gap).coerceAtLeast(gap)
            }
        }
        caption.requestLayout()
    }

    /** Target bounds relative to the content root, which is what the overlay is sized to. */
    private fun View.boundsInRoot(paddingPx: Int): RectF {
        val targetLoc = IntArray(2).also { getLocationInWindow(it) }
        val rootLoc = IntArray(2).also { root.getLocationInWindow(it) }
        val r = Rect(
            targetLoc[0] - rootLoc[0],
            targetLoc[1] - rootLoc[1],
            targetLoc[0] - rootLoc[0] + width,
            targetLoc[1] - rootLoc[1] + height,
        )
        return RectF(
            (r.left - paddingPx).toFloat(),
            (r.top - paddingPx).toFloat(),
            (r.right + paddingPx).toFloat(),
            (r.bottom + paddingPx).toFloat(),
        )
    }

    private fun dismiss(completed: Boolean) {
        if (dismissed) return
        dismissed = true
        root.removeView(binding.root)
        root.removeView(spotlight)
        onFinished(completed)
    }

    companion object {
        private const val GAP_PX = 24f

        /**
         * Builds and starts a tour. Returns null when there is nothing to show, so the
         * caller can treat "no tour" and "tour finished" the same way.
         */
        fun show(
            activity: Activity,
            steps: List<CoachStep>,
            onFinished: (completed: Boolean) -> Unit = {},
        ): CoachMarkSequence? {
            if (steps.isEmpty()) return null
            return CoachMarkSequence(activity, steps, onFinished).also { it.start() }
        }
    }
}

package com.instafact.app.ui.walkthrough

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.instafact.app.R

/**
 * Entrance choreography for each walkthrough illustration. Every page hides its pieces on bind
 * and replays them when the page settles, so swiping back and forth re-runs the animation.
 */
object WalkthroughAnimator {

    private const val POP_DURATION = 420L
    private const val FADE_DURATION = 460L
    private const val STAGGER = 140L

    private fun welcomeSequence(root: View): List<View> = listOfNotNull(
        root.findViewById(R.id.welcomeImageView),
        root.findViewById(R.id.welcomeLogoCard),
        root.findViewById(R.id.welcomeTitleTextView),
        root.findViewById(R.id.welcomeBrandTextView),
        root.findViewById(R.id.welcomeSubtitleTextView),
    )

    fun prepareWelcome(root: View) = prepareViews(welcomeSequence(root))

    fun playWelcome(root: View) = playViews(welcomeSequence(root))

    /** Views animated on each story page, in the order they should appear. */
    private fun sequenceFor(root: View, position: Int): List<View> = when (position) {
        1 -> listOfNotNull(
            root.findViewById(R.id.personImageView),
            root.findViewById(R.id.reelOne),
            root.findViewById(R.id.reelTwo),
            root.findViewById(R.id.reelThree),
        )

        2 -> listOfNotNull(
            root.findViewById(R.id.phoneImageView),
            root.findViewById(R.id.flowArrowImageView),
            root.findViewById(R.id.logoTile),
            root.findViewById(R.id.resultCard),
        )

        3 -> listOfNotNull(
            root.findViewById(R.id.verdictHeaderRow),
            root.findViewById(R.id.sourcesSection),
        )

        else -> listOfNotNull(
            root.findViewById(R.id.familyImageView),
            root.findViewById(R.id.checkedCard),
            root.findViewById(R.id.sharedReelCard),
        )
    }

    fun prepare(root: View, position: Int) = prepareViews(sequenceFor(root, position))

    fun play(root: View, position: Int) = playViews(sequenceFor(root, position))

    private fun prepareViews(views: List<View>) {
        views.forEachIndexed { index, view ->
            view.animate().cancel()
            view.alpha = 0f
            if (index == 0) {
                // The backdrop photo/header just fades; scaling a full-bleed image looks wrong.
                view.scaleX = 1f
                view.scaleY = 1f
                view.translationY = 0f
            } else {
                view.scaleX = 0.82f
                view.scaleY = 0.82f
                view.translationY = view.resources.displayMetrics.density * 14f
            }
        }
    }

    private fun playViews(views: List<View>) {
        views.forEachIndexed { index, view ->
            view.animate().cancel()
            if (index == 0) {
                view.alpha = 0f
                view.animate()
                    .alpha(1f)
                    .setDuration(FADE_DURATION)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                view.alpha = 0f
                view.scaleX = 0.82f
                view.scaleY = 0.82f
                view.translationY = view.resources.displayMetrics.density * 14f
                view.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setStartDelay(STAGGER * index)
                    .setDuration(POP_DURATION)
                    .setInterpolator(OvershootInterpolator(1.4f))
                    .start()
            }
        }
    }
}

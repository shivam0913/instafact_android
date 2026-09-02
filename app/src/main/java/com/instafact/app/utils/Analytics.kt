package com.instafact.app.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Google Analytics for Firebase, behind one typed surface.
 *
 * Every call routes through here rather than touching FirebaseAnalytics directly, so
 * event and parameter names stay spelled consistently - GA silently creates a brand new
 * event for a typo, and those cannot be merged or renamed after the fact.
 *
 * Nothing here logs personally identifying data. Phone numbers, OTPs, names, tokens and
 * raw reel URLs stay out of the payloads on purpose: GA forbids PII, and a reel URL is
 * effectively a user-content identifier. Reels are identified by query id and platform.
 */
object Analytics {

    private const val TAG = "Analytics"

    private var analytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        analytics = runCatching { Firebase.analytics }
            .onFailure { Log.w(TAG, "Firebase Analytics unavailable; events will be dropped.", it) }
            .getOrNull()
    }

    /** Screen views power GA's funnel and retention reports, so every screen reports one. */
    fun logScreenView(screenName: String, screenClass: String) {
        log(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            bundleOf(
                FirebaseAnalytics.Param.SCREEN_NAME to screenName,
                FirebaseAnalytics.Param.SCREEN_CLASS to screenClass,
            ),
        )
    }

    // ---- Onboarding & auth funnel -------------------------------------------------

    fun logWalkthroughPageViewed(pageIndex: Int) {
        log(EVENT_WALKTHROUGH_PAGE_VIEWED, bundleOf(PARAM_PAGE_INDEX to pageIndex.toLong()))
    }

    fun logWalkthroughCompleted(skipped: Boolean) {
        log(EVENT_WALKTHROUGH_COMPLETED, bundleOf(PARAM_SKIPPED to skipped))
    }

    /** Country code, never the phone number: it is a useful segment and is not PII. */
    fun logOtpRequested(countryCode: String, isResend: Boolean) {
        log(
            EVENT_OTP_REQUESTED,
            bundleOf(PARAM_COUNTRY_CODE to countryCode, PARAM_IS_RESEND to isResend),
        )
    }

    fun logOtpVerified(success: Boolean, failureReason: String? = null) {
        val params = bundleOf(PARAM_SUCCESS to success)
        failureReason?.let { params.putString(PARAM_FAILURE_REASON, it.take(100)) }
        log(EVENT_OTP_VERIFIED, params)
    }

    /**
     * New-vs-returning is deliberately absent: verify-otp does not tell the client which
     * one happened, and guessing from local state would miscount reinstalls and
     * re-logins. Add an `is_new_user` flag to the backend response to segment this.
     */
    fun logLoginCompleted() {
        log(FirebaseAnalytics.Event.LOGIN, bundleOf(PARAM_METHOD to "phone_otp"))
        log(EVENT_LOGIN_COMPLETED, Bundle())
    }

    fun logProfileCompleted(skipped: Boolean) {
        log(EVENT_PROFILE_COMPLETED, bundleOf(PARAM_SKIPPED to skipped))
    }

    fun logLogout() = log(EVENT_LOGOUT, Bundle())

    // ---- The core funnel: submit a reel, get a verdict -----------------------------

    /**
     * [source] separates a link shared into the app from one pasted inside it - the
     * share-sheet path is the product's main entry point and deserves its own number.
     */
    fun logSubmitStarted(platform: String, source: String) {
        log(
            EVENT_SUBMIT_STARTED,
            bundleOf(PARAM_PLATFORM to platform, PARAM_SOURCE to source),
        )
    }

    fun logSubmitSucceeded(platform: String, queryId: Int) {
        log(
            EVENT_SUBMIT_SUCCEEDED,
            bundleOf(PARAM_PLATFORM to platform, PARAM_QUERY_ID to queryId.toLong()),
        )
    }

    fun logSubmitFailed(platform: String, reason: String) {
        log(
            EVENT_SUBMIT_FAILED,
            bundleOf(PARAM_PLATFORM to platform, PARAM_FAILURE_REASON to reason.take(100)),
        )
    }

    /**
     * The payoff moment. Verdict and confidence are the two fields that answer "what is
     * the app actually telling people?", so they are worth having in GA directly.
     */
    fun logResultViewed(queryId: Int, verdict: String?, confidence: Int?, platform: String) {
        val params = bundleOf(
            PARAM_QUERY_ID to queryId.toLong(),
            PARAM_VERDICT to (verdict ?: "unknown"),
            PARAM_PLATFORM to platform,
        )
        confidence?.let { params.putLong(PARAM_CONFIDENCE, it.toLong()) }
        log(EVENT_RESULT_VIEWED, params)
    }

    fun logResultStillProcessing(queryId: Int) {
        log(EVENT_RESULT_STILL_PROCESSING, bundleOf(PARAM_QUERY_ID to queryId.toLong()))
    }

    fun logResultFailed(queryId: Int) {
        log(EVENT_RESULT_FAILED, bundleOf(PARAM_QUERY_ID to queryId.toLong()))
    }

    /** Retry volume is the signal for how often checks fail in a recoverable way. */
    fun logResultRetried(queryId: Int, platform: String) {
        log(
            EVENT_RESULT_RETRIED,
            bundleOf(PARAM_QUERY_ID to queryId.toLong(), PARAM_PLATFORM to platform),
        )
    }

    // ---- Engagement with a result --------------------------------------------------

    fun logFeedbackSubmitted(queryId: Int, feedbackType: String, verdict: String?) {
        log(
            EVENT_FEEDBACK_SUBMITTED,
            bundleOf(
                PARAM_QUERY_ID to queryId.toLong(),
                PARAM_FEEDBACK_TYPE to feedbackType,
                PARAM_VERDICT to (verdict ?: "unknown"),
            ),
        )
    }

    fun logResultShared(queryId: Int, verdict: String?) {
        log(
            FirebaseAnalytics.Event.SHARE,
            bundleOf(
                FirebaseAnalytics.Param.CONTENT_TYPE to "fact_check_result",
                FirebaseAnalytics.Param.ITEM_ID to queryId.toString(),
                PARAM_VERDICT to (verdict ?: "unknown"),
            ),
        )
    }

    fun logReferencesOpened(queryId: Int, referenceCount: Int) {
        log(
            EVENT_REFERENCES_OPENED,
            bundleOf(
                PARAM_QUERY_ID to queryId.toLong(),
                PARAM_REFERENCE_COUNT to referenceCount.toLong(),
            ),
        )
    }

    fun logReferenceLinkOpened(queryId: Int) {
        log(EVENT_REFERENCE_LINK_OPENED, bundleOf(PARAM_QUERY_ID to queryId.toLong()))
    }

    fun logSourceVideoOpened(queryId: Int, platform: String) {
        log(
            EVENT_SOURCE_VIDEO_OPENED,
            bundleOf(PARAM_QUERY_ID to queryId.toLong(), PARAM_PLATFORM to platform),
        )
    }

    // ---- Follow-up chat -------------------------------------------------------------

    fun logChatOpened(queryId: Int) {
        log(EVENT_CHAT_OPENED, bundleOf(PARAM_QUERY_ID to queryId.toLong()))
    }

    /** Message length only - the question text itself is user content and stays local. */
    fun logChatMessageSent(queryId: Int, messageLength: Int) {
        log(
            EVENT_CHAT_MESSAGE_SENT,
            bundleOf(
                PARAM_QUERY_ID to queryId.toLong(),
                PARAM_MESSAGE_LENGTH to messageLength.toLong(),
            ),
        )
    }

    // ---- Explore & history -----------------------------------------------------------

    /** [section] is the explore row the tap came from: trending, shared or recent. */
    fun logExploreItemOpened(queryId: Int, section: String) {
        log(
            EVENT_EXPLORE_ITEM_OPENED,
            bundleOf(PARAM_QUERY_ID to queryId.toLong(), PARAM_SECTION to section),
        )
    }

    fun logHistoryItemOpened(queryId: Int) {
        log(EVENT_HISTORY_ITEM_OPENED, bundleOf(PARAM_QUERY_ID to queryId.toLong()))
    }

    fun logHistoryItemDeleted(queryId: Int) {
        log(EVENT_HISTORY_ITEM_DELETED, bundleOf(PARAM_QUERY_ID to queryId.toLong()))
    }

    // ---- Notifications ----------------------------------------------------------------

    fun logPushReceived(queryId: Int?) {
        log(EVENT_PUSH_RECEIVED, bundleOf(PARAM_QUERY_ID to (queryId?.toLong() ?: -1L)))
    }

    fun logPushOpened(queryId: Int?) {
        log(EVENT_PUSH_OPENED, bundleOf(PARAM_QUERY_ID to (queryId?.toLong() ?: -1L)))
    }

    fun logNotificationPermission(granted: Boolean) {
        log(EVENT_NOTIFICATION_PERMISSION, bundleOf(PARAM_GRANTED to granted))
    }

    // ---- First-run tour -----------------------------------------------------------------

    fun logTourStarted() = log(EVENT_TOUR_STARTED, Bundle())

    /** completed=false means they hit Skip, which is the drop-off signal worth watching. */
    fun logTourFinished(completed: Boolean) {
        log(EVENT_TOUR_FINISHED, bundleOf(PARAM_COMPLETED to completed))
    }

    // ---- Unsupported platforms ---------------------------------------------------------

    /** Counts demand for platforms we do not fact-check yet, to prioritise what to build. */
    fun logUnsupportedPlatform(platform: String) {
        log(EVENT_UNSUPPORTED_PLATFORM, bundleOf(PARAM_PLATFORM to platform))
    }

    // ---- Rating flow ---------------------------------------------------------------------

    fun logRatingPromptShown(trigger: String) {
        log(EVENT_RATING_PROMPT_SHOWN, bundleOf(PARAM_TRIGGER to trigger))
    }

    fun logRatingDismissed(trigger: String) {
        log(EVENT_RATING_DISMISSED, bundleOf(PARAM_TRIGGER to trigger))
    }

    fun logRatingSubmitted(rating: Int, trigger: String) {
        log(
            EVENT_RATING_SUBMITTED,
            bundleOf(PARAM_RATING to rating.toLong(), PARAM_TRIGGER to trigger),
        )
    }

    /**
     * Low-rating feedback.
     *
     * GA truncates string params at 100 characters, so a long comment arrives clipped -
     * the length is sent alongside to make that visible. If full comments matter, they
     * need a backend endpoint; GA is not a feedback inbox.
     */
    fun logRatingFeedback(rating: Int, reasons: List<String>, comment: String, trigger: String) {
        val params = bundleOf(
            PARAM_RATING to rating.toLong(),
            PARAM_TRIGGER to trigger,
            PARAM_REASONS to reasons.joinToString(",").ifBlank { "none" },
            PARAM_REASON_COUNT to reasons.size.toLong(),
            PARAM_COMMENT_LENGTH to comment.length.toLong(),
        )
        if (comment.isNotBlank()) params.putString(PARAM_COMMENT, comment.take(100))
        log(EVENT_RATING_FEEDBACK, params)
    }

    // ---- User properties ---------------------------------------------------------------

    /**
     * User properties segment every report (e.g. "retention among users who ran 5+
     * checks"). GA caps these at 25 per project, so only durable traits belong here.
     */
    fun setUserProperties(countryCode: String?, gender: String?, ageGroup: String?) {
        countryCode?.let { setUserProperty(USER_PROPERTY_COUNTRY_CODE, it) }
        gender?.let { setUserProperty(USER_PROPERTY_GENDER, it) }
        ageGroup?.let { setUserProperty(USER_PROPERTY_AGE_GROUP, it) }
    }

    fun setLifetimeChecksBucket(totalChecks: Int) {
        val bucket = when {
            totalChecks <= 0 -> "0"
            totalChecks == 1 -> "1"
            totalChecks <= 5 -> "2-5"
            totalChecks <= 20 -> "6-20"
            else -> "20+"
        }
        setUserProperty(USER_PROPERTY_CHECKS_BUCKET, bucket)
    }

    /**
     * GA's user id is what stitches one person's sessions together across devices.
     * The backend user id is a stable opaque integer, so it is safe here; a phone
     * number never would be.
     */
    fun setUserId(userId: Int?) {
        runCatching { analytics?.setUserId(userId?.toString()) }
            .onFailure { Log.w(TAG, "Could not set analytics user id.", it) }
    }

    private fun setUserProperty(name: String, value: String) {
        runCatching { analytics?.setUserProperty(name, value.take(36)) }
            .onFailure { Log.w(TAG, "Could not set user property $name.", it) }
    }

    /** Analytics must never be the reason a screen crashes. */
    private fun log(event: String, params: Bundle) {
        val instance = analytics
        if (instance == null) {
            Log.d(TAG, "Dropped event '$event' (analytics not initialized).")
            return
        }
        runCatching { instance.logEvent(event, params) }
            .onFailure { Log.w(TAG, "Could not log event '$event'.", it) }
    }

    // GA event names: <=40 chars, letters/digits/underscores, no "firebase_" prefix.
    private const val EVENT_WALKTHROUGH_PAGE_VIEWED = "walkthrough_page_viewed"
    private const val EVENT_WALKTHROUGH_COMPLETED = "walkthrough_completed"
    private const val EVENT_OTP_REQUESTED = "otp_requested"
    private const val EVENT_OTP_VERIFIED = "otp_verified"
    private const val EVENT_LOGIN_COMPLETED = "login_completed"
    private const val EVENT_PROFILE_COMPLETED = "profile_completed"
    private const val EVENT_LOGOUT = "logout"
    private const val EVENT_SUBMIT_STARTED = "submit_started"
    private const val EVENT_SUBMIT_SUCCEEDED = "submit_succeeded"
    private const val EVENT_SUBMIT_FAILED = "submit_failed"
    private const val EVENT_RESULT_VIEWED = "result_viewed"
    private const val EVENT_RESULT_STILL_PROCESSING = "result_still_processing"
    private const val EVENT_RESULT_FAILED = "result_failed"
    private const val EVENT_RESULT_RETRIED = "result_retried"
    private const val EVENT_FEEDBACK_SUBMITTED = "feedback_submitted"
    private const val EVENT_REFERENCES_OPENED = "references_opened"
    private const val EVENT_REFERENCE_LINK_OPENED = "reference_link_opened"
    private const val EVENT_SOURCE_VIDEO_OPENED = "source_video_opened"
    private const val EVENT_CHAT_OPENED = "chat_opened"
    private const val EVENT_CHAT_MESSAGE_SENT = "chat_message_sent"
    private const val EVENT_EXPLORE_ITEM_OPENED = "explore_item_opened"
    private const val EVENT_HISTORY_ITEM_OPENED = "history_item_opened"
    private const val EVENT_HISTORY_ITEM_DELETED = "history_item_deleted"
    private const val EVENT_PUSH_RECEIVED = "push_received"
    private const val EVENT_PUSH_OPENED = "push_opened"
    private const val EVENT_NOTIFICATION_PERMISSION = "notification_permission"
    private const val EVENT_UNSUPPORTED_PLATFORM = "unsupported_platform"
    private const val EVENT_TOUR_STARTED = "tour_started"
    private const val EVENT_TOUR_FINISHED = "tour_finished"
    private const val EVENT_RATING_PROMPT_SHOWN = "rating_prompt_shown"
    private const val EVENT_RATING_DISMISSED = "rating_dismissed"
    private const val EVENT_RATING_SUBMITTED = "rating_submitted"
    private const val EVENT_RATING_FEEDBACK = "rating_feedback"

    private const val PARAM_PAGE_INDEX = "page_index"
    private const val PARAM_SKIPPED = "skipped"
    private const val PARAM_COUNTRY_CODE = "country_code"
    private const val PARAM_IS_RESEND = "is_resend"
    private const val PARAM_SUCCESS = "success"
    private const val PARAM_FAILURE_REASON = "failure_reason"
    private const val PARAM_METHOD = "method"
    private const val PARAM_PLATFORM = "platform"
    private const val PARAM_SOURCE = "source"
    private const val PARAM_QUERY_ID = "query_id"
    private const val PARAM_VERDICT = "verdict"
    private const val PARAM_CONFIDENCE = "confidence"
    private const val PARAM_FEEDBACK_TYPE = "feedback_type"
    private const val PARAM_REFERENCE_COUNT = "reference_count"
    private const val PARAM_MESSAGE_LENGTH = "message_length"
    private const val PARAM_SECTION = "section"
    private const val PARAM_TRIGGER = "trigger"
    private const val PARAM_COMPLETED = "completed"
    private const val PARAM_RATING = "rating"
    private const val PARAM_REASONS = "reasons"
    private const val PARAM_REASON_COUNT = "reason_count"
    private const val PARAM_COMMENT = "comment"
    private const val PARAM_COMMENT_LENGTH = "comment_length"
    private const val PARAM_GRANTED = "granted"

    private const val USER_PROPERTY_COUNTRY_CODE = "country_code"
    private const val USER_PROPERTY_GENDER = "gender"
    private const val USER_PROPERTY_AGE_GROUP = "age_group"
    private const val USER_PROPERTY_CHECKS_BUCKET = "checks_bucket"

    const val SOURCE_SHARE_SHEET = "share_sheet"
    const val SOURCE_IN_APP = "in_app"
    const val SOURCE_NOTIFICATION = "notification"
}

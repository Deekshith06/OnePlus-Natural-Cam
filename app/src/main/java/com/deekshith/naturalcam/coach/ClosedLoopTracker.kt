package com.deekshith.naturalcam.coach

import android.util.Log
import kotlin.math.abs

/**
 * Closed-Loop Verification Tracker.
 * When the user taps "Apply", re-measures the target metric over subsequent frames.
 * If the metric did not improve by at least 40% (CoachThresholds.CLOSED_LOOP_REQUIRED_IMPROVEMENT),
 * marks the suggestion as ineffective and suppresses it for 30 seconds.
 */
class ClosedLoopTracker {

    private data class PendingEvaluation(
        val suggestion: CoachSuggestion,
        val applyTimestampMs: Long,
        val baselineDelta: Float,
        var sampleCount: Int = 0,
        var bestObservedDelta: Float = Float.MAX_VALUE
    )

    private val activeEvaluations = mutableMapOf<String, PendingEvaluation>()
    private val suppressedSuggestions = mutableMapOf<String, Long>() // suggestionId -> unsuppressTimestampMs

    var totalEvaluations: Int = 0
        private set
    var successfulEvaluations: Int = 0
        private set

    val successRate: Float
        get() = if (totalEvaluations > 0) successfulEvaluations.toFloat() / totalEvaluations else 1.0f

    /**
     * Called when the user taps "Apply" on a suggestion pill.
     */
    fun onSuggestionApplied(suggestion: CoachSuggestion) {
        val now = System.currentTimeMillis()
        val baselineDelta = abs(suggestion.baselineValue - suggestion.expectedTarget)
        activeEvaluations[suggestion.id] = PendingEvaluation(
            suggestion = suggestion,
            applyTimestampMs = now,
            baselineDelta = baselineDelta,
            sampleCount = 0,
            bestObservedDelta = baselineDelta
        )
        Log.i(TAG, "Closed-loop tracking initiated for [${suggestion.id}]: baselineDelta=$baselineDelta, target=${suggestion.expectedTarget}")
    }

    /**
     * Called when the user taps dismiss (✕) on a suggestion pill.
     */
    fun onSuggestionDismissed(suggestionId: String) {
        val unsuppressTime = System.currentTimeMillis() + CoachThresholds.SUPPRESSION_AFTER_DISMISS_MS
        suppressedSuggestions[suggestionId] = unsuppressTime
        activeEvaluations.remove(suggestionId)
        Log.i(TAG, "Suggestion [$suggestionId] manually dismissed, suppressed until $unsuppressTime")
    }

    /**
     * Checks if a suggestion ID is currently suppressed due to inefficacy or dismissal.
     */
    fun isSuppressed(suggestionId: String): Boolean {
        val unsuppressTime = suppressedSuggestions[suggestionId] ?: return false
        val now = System.currentTimeMillis()
        if (now >= unsuppressTime) {
            suppressedSuggestions.remove(suggestionId)
            return false
        }
        return true
    }

    /**
     * Feed live measured state each analysis frame to verify active evaluations.
     */
    fun update(frameState: MeasuredFrameState) {
        val now = System.currentTimeMillis()
        val completedIds = mutableListOf<String>()

        for ((id, eval) in activeEvaluations) {
            val elapsedMs = now - eval.applyTimestampMs
            
            // Extract current metric value from frameState
            val currentMetricValue = extractMetricValue(eval.suggestion.trackedMetric, frameState)
            val currentDelta = abs(currentMetricValue - eval.suggestion.expectedTarget)
            
            if (currentDelta < eval.bestObservedDelta) {
                eval.bestObservedDelta = currentDelta
            }
            eval.sampleCount++

            // Evaluate after minimum 4 samples or timeout
            if (eval.sampleCount >= 4 || elapsedMs >= CoachThresholds.CLOSED_LOOP_TIMEOUT_MS) {
                totalEvaluations++
                val baselineDelta = eval.baselineDelta
                // If baseline delta was non-zero, check relative improvement
                val improvement = if (baselineDelta > 0.0001f) {
                    (baselineDelta - eval.bestObservedDelta) / baselineDelta
                } else {
                    1.0f
                }

                val isEffective = improvement >= CoachThresholds.CLOSED_LOOP_REQUIRED_IMPROVEMENT ||
                        eval.bestObservedDelta <= 0.02f // Practically fully resolved

                if (isEffective) {
                    successfulEvaluations++
                    Log.i(TAG, "Closed-loop SUCCESS for [${eval.suggestion.id}]: improved by ${(improvement * 100).toInt()}% (bestDelta=${eval.bestObservedDelta})")
                } else {
                    // Mark ineffective, suppress for 30s
                    val unsuppressTime = now + CoachThresholds.SUPPRESSION_AFTER_DISMISS_MS
                    suppressedSuggestions[eval.suggestion.id] = unsuppressTime
                    Log.w(TAG, "Closed-loop INEFFECTIVE for [${eval.suggestion.id}]: improvement only ${(improvement * 100).toInt()}% (< 40%). Suppressed for 30s.")
                }
                completedIds.add(id)
            }
        }

        completedIds.forEach { activeEvaluations.remove(it) }
    }

    private fun extractMetricValue(metric: TrackedMetric, state: MeasuredFrameState): Float {
        return when (metric) {
            TrackedMetric.HIGHLIGHT_CLIPPING -> state.highlightClippedPct
            TrackedMetric.SHADOW_CRUSHING -> state.shadowCrushedPct
            TrackedMetric.HORIZON_ROLL -> abs(state.horizonRollDeg)
            TrackedMetric.SUBJECT_HEIGHT_RATIO -> {
                state.poseState?.boundingBox?.height() ?: 0.6f
            }
            TrackedMetric.POSE_MATCH_SCORE -> 0.85f
            TrackedMetric.CAMERA_STABILITY -> state.angularSpeedRadS
        }
    }

    companion object {
        private const val TAG = "ClosedLoopTracker"
    }
}

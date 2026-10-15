package com.deekshith.naturalcam.coach

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ClosedLoopTrackerTest {

    private lateinit var tracker: ClosedLoopTracker

    @Before
    fun setUp() {
        tracker = ClosedLoopTracker()
    }

    @Test
    fun testAppliedSuggestion_whenMetricImproves_succeedsAndIsNotSuppressed() {
        val suggestion = CoachSuggestion(
            id = "test_highlight",
            type = SuggestionType.EXPOSURE,
            priority = 1,
            shortLabel = "EV -0.7",
            evidence = "15% highlights clipped -> EV -0.7",
            action = CoachAction.AdjustEv(-0.7f),
            trackedMetric = TrackedMetric.HIGHLIGHT_CLIPPING,
            baselineValue = 0.15f,
            expectedTarget = 0.02f
        )

        tracker.onSuggestionApplied(suggestion)

        // Feed 4 frames with improved metric (0.03f vs baseline 0.15f -> > 80% improvement)
        repeat(4) {
            val state = MeasuredFrameState(highlightClippedPct = 0.03f)
            tracker.update(state)
        }

        // Evaluation should complete with success
        assertEquals(1, tracker.totalEvaluations)
        assertEquals(1, tracker.successfulEvaluations)
        assertFalse("Suggestion should NOT be suppressed after successful resolution", tracker.isSuppressed("test_highlight"))
    }

    @Test
    fun testAppliedSuggestion_whenMetricDoesNotImprove_marksIneffectiveAndSuppresses30s() {
        val suggestion = CoachSuggestion(
            id = "test_ineffective_highlight",
            type = SuggestionType.EXPOSURE,
            priority = 1,
            shortLabel = "EV -0.3",
            evidence = "18% highlights clipped -> EV -0.3",
            action = CoachAction.AdjustEv(-0.3f),
            trackedMetric = TrackedMetric.HIGHLIGHT_CLIPPING,
            baselineValue = 0.18f,
            expectedTarget = 0.02f
        )

        tracker.onSuggestionApplied(suggestion)

        // Feed 4 frames where metric did NOT improve (still 0.17f vs baseline 0.18f -> < 40% improvement)
        repeat(4) {
            val state = MeasuredFrameState(highlightClippedPct = 0.17f)
            tracker.update(state)
        }

        assertEquals(1, tracker.totalEvaluations)
        assertEquals(0, tracker.successfulEvaluations)
        assertTrue("Suggestion MUST be suppressed for 30s when ineffective", tracker.isSuppressed("test_ineffective_highlight"))
    }

    @Test
    fun testManualDismiss_immediatelySuppressesSuggestion() {
        val ruleId = "rule_horizon_roll"
        assertFalse(tracker.isSuppressed(ruleId))

        tracker.onSuggestionDismissed(ruleId)
        assertTrue("Suggestion MUST be suppressed after user dismisses it", tracker.isSuppressed(ruleId))
    }
}

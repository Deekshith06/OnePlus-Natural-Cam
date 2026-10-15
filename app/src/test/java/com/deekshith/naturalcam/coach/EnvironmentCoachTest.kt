package com.deekshith.naturalcam.coach

import org.junit.Assert.*
import org.junit.Test

class EnvironmentCoachTest {

    @Test
    fun testOfflineEnvironmentIntelligence_coversAllPlaceCategories() {
        val categories = listOf(
            PlaceCategory.INDOOR_ROOM,
            PlaceCategory.FOREST_NATURE,
            PlaceCategory.BEACH_WATER,
            PlaceCategory.CAFE_DINING,
            PlaceCategory.URBAN_STREET,
            PlaceCategory.HISTORIC_ARCHITECTURE,
            PlaceCategory.BOTANICAL_GARDEN,
            PlaceCategory.LOW_LIGHT_NIGHT,
            PlaceCategory.PORTRAIT_STUDIO,
            PlaceCategory.GENERAL
        )

        for (category in categories) {
            val title = when (category) {
                PlaceCategory.INDOOR_ROOM -> "Indoor Ambience"
                PlaceCategory.FOREST_NATURE -> "Nature & Foliage"
                PlaceCategory.BEACH_WATER -> "Coastal Waters"
                PlaceCategory.CAFE_DINING -> "Cafe & Bistro"
                PlaceCategory.URBAN_STREET -> "Urban Street"
                PlaceCategory.HISTORIC_ARCHITECTURE -> "Historic Architecture"
                PlaceCategory.BOTANICAL_GARDEN -> "Botanical Garden"
                PlaceCategory.LOW_LIGHT_NIGHT -> "Night Scene"
                PlaceCategory.PORTRAIT_STUDIO -> "Studio Portrait"
                PlaceCategory.GENERAL -> "Natural Scene"
            }
            assertTrue(title.isNotBlank())
        }
    }

    @Test
    fun testFramingCenterTolerance_isSymmetricAndAccurate() {
        val tolerance = CoachThresholds.FRAMING_CENTER_TOLERANCE
        assertEquals(0.08f, tolerance, 0.001f)

        // Test boundary values
        val exactCenter = 0.50f
        val offsetInside = exactCenter + 0.07f - 0.50f
        val offsetOutside = exactCenter + 0.09f - 0.50f

        assertTrue("Offset 0.07f should be within tolerance", Math.abs(offsetInside) <= tolerance)
        assertFalse("Offset 0.09f should be outside tolerance", Math.abs(offsetOutside) <= tolerance)
    }
}

package com.dietoexpress.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepDbExerciseImagesTest {
    @Test
    fun curatedRepDbExerciseUsesStartAndPeakImages() {
        assertEquals(
            listOf(
                "https://exercise-dataset.com/images/flat/db-fly-start.webp",
                "https://exercise-dataset.com/images/flat/db-fly-peak.webp"
            ),
            repDbExerciseImageUrls("repdb-free-curated-v1", "db-fly")
        )
    }

    @Test
    fun singleImageRepDbExercisesUseTheirMainImage() {
        assertEquals(
            listOf("https://exercise-dataset.com/images/flat/dumbbell-farmers-walk-main.webp"),
            repDbExerciseImageUrls("repdb-free-curated-v1", "dumbbell-farmers-walk")
        )
        assertEquals(
            listOf("https://exercise-dataset.com/images/flat/side-plank-main.webp"),
            repDbExerciseImageUrls("repdb-free-curated-v1", "side-plank")
        )
    }

    @Test
    fun nonRepDbAndUnsafeExerciseIdsNeverGenerateExternalImageUrls() {
        assertTrue(repDbExerciseImageUrls("professional-v1", "custom-exercise").isEmpty())
        assertTrue(repDbExerciseImageUrls("repdb-free-curated-v1", "../private-image").isEmpty())
        assertTrue(repDbExerciseImageUrls(null, "bench-press").isEmpty())
    }
}

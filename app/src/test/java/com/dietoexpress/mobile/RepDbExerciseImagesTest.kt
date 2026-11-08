package com.dietoexpress.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pictures are requested from our own backend only; no third-party URL is ever built by the app. */
class RepDbExerciseImagesTest {
    private val base = "https://example.test/api/"

    @Test
    fun buildsTheBackendImageUrlForAnExerciseAndSlot() {
        assertEquals(
            "https://example.test/api/portal/training/exercise-images/db-fly/0",
            exerciseImageUrl(base, "db-fly", 0)
        )
        assertEquals(
            "https://example.test/api/portal/training/exercise-images/db-fly/1",
            exerciseImageUrl("https://example.test/api", "db-fly", 1)
        )
    }

    @Test
    fun unsafeIdsAndOutOfRangeSlotsNeverProduceAnUrl() {
        assertNull(exerciseImageUrl(base, "../private-image", 0))
        assertNull(exerciseImageUrl(base, "a/b", 0))
        assertNull(exerciseImageUrl(base, "", 0))
        assertNull(exerciseImageUrl(base, "db-fly", 4))
        assertNull(exerciseImageUrl(base, "db-fly", -1))
    }

    @Test
    fun theUrlNeverPointsToAThirdPartyHost() {
        val url = exerciseImageUrl(base, "db-fly", 0)!!
        assertEquals(true, url.startsWith(base.trimEnd('/')))
    }
}

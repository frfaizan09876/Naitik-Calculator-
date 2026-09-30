package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SolvedQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GyanLens", appName)
    }

    @Test
    fun `solved question entity holds data correctly`() {
        val question = SolvedQuestion(
            id = 1L,
            subject = "गणित (Math)",
            questionText = "Solve 2x + 5 = 15",
            solutionText = "x = 5",
            shortAnswer = "x = 5"
        )
        assertEquals(1L, question.id)
        assertEquals("गणित (Math)", question.subject)
        assertEquals("x = 5", question.shortAnswer)
        assertTrue(question.timestamp > 0)
    }
}

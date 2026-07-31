package com.arcticalphawolf.arcticrf

import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityCrashTest {

    @Test
    fun `launch MainActivity and surface any exception`() {
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    println("ACTIVITY_LAUNCHED_OK: $activity")
                }
            }
        } catch (t: Throwable) {
            println("ACTIVITY_LAUNCH_THREW: ${t.javaClass.name}: ${t.message}")
            t.printStackTrace()
            throw t
        }
    }
}

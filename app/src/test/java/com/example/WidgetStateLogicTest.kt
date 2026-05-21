package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStateLogicTest {

    @Test
    fun testGhostMode() {
        val ctx1 = AtmosphereContext(isOffline = true)
        assertEquals(WidgetState.GHOST, evaluateState(ctx1))

        val ctx2 = AtmosphereContext(battery = 10)
        assertEquals(WidgetState.GHOST, evaluateState(ctx2))
    }

    @Test
    fun testFocusMode() {
        // FOCUS has priority over BEAST, REWIND, etc., but not over GHOST
        val ctx = AtmosphereContext(hasFocusEvent = true)
        assertEquals(WidgetState.FOCUS, evaluateState(ctx))
        
        val ctxQuiet = AtmosphereContext(ambientNoiseLevel = 45.0)
        assertEquals(WidgetState.FOCUS, evaluateState(ctxQuiet))
        
        // GHOST wins over FOCUS
        val ctxGhost = AtmosphereContext(hasFocusEvent = true, isOffline = true)
        assertEquals(WidgetState.GHOST, evaluateState(ctxGhost))
    }

    @Test
    fun testBeastMode() {
        val ctx1 = AtmosphereContext(address = "The Gym", isHeadphonesConnected = true)
        assertEquals(WidgetState.BEAST, evaluateState(ctx1))

        val ctx2 = AtmosphereContext(detectedActivity = com.google.android.gms.location.DetectedActivity.RUNNING)
        assertEquals(WidgetState.BEAST, evaluateState(ctx2))
    }

    @Test
    fun testRewindMode() {
        val ctx = AtmosphereContext(address = "Sweet Home", timeHour = 21)
        assertEquals(WidgetState.REWIND, evaluateState(ctx))

        // Time not met (before 18:00)
        val ctxFail = AtmosphereContext(address = "Home", timeHour = 17)
        assertEquals(WidgetState.DISCOVERY, evaluateState(ctxFail))

        // Tired -> low battery + evening time
        val ctxTired = AtmosphereContext(timeHour = 19, battery = 40)
        assertEquals(WidgetState.REWIND, evaluateState(ctxTired))

        // Relax -> quiet + evening time
        val ctxQuiet = AtmosphereContext(timeHour = 19, ambientNoiseLevel = 45.0)
        assertEquals(WidgetState.REWIND, evaluateState(ctxQuiet))
    }

    @Test
    fun testCozyMode() {
        val ctx1 = AtmosphereContext(weather = "Rain")
        assertEquals(WidgetState.COZY, evaluateState(ctx1))

        val ctx2 = AtmosphereContext(weather = "Storm")
        assertEquals(WidgetState.COZY, evaluateState(ctx2))

        val ctx3 = AtmosphereContext(weather = "Snow")
        assertEquals(WidgetState.COZY, evaluateState(ctx3))
    }

    @Test
    fun testCrowdMode() {
        val ctx1 = AtmosphereContext(address = "A Party")
        assertEquals(WidgetState.CROWD, evaluateState(ctx1))

        val ctx2 = AtmosphereContext(isSocialMode = true)
        assertEquals(WidgetState.CROWD, evaluateState(ctx2))
        
        val ctxLoud = AtmosphereContext(sustainedLoudNoise = true)
        assertEquals(WidgetState.CROWD, evaluateState(ctxLoud))
    }

    @Test
    fun testDiscoveryMode() {
        val ctx = AtmosphereContext()
        assertEquals(WidgetState.DISCOVERY, evaluateState(ctx))
    }
}

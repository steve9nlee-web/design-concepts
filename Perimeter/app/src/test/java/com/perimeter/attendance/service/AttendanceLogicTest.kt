package com.perimeter.attendance.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * These cover the decisions that put hours on someone's payslip, so they are
 * written as the scenarios that actually happen on a shift rather than as
 * method-by-method coverage.
 */
class AttendanceLogicTest {

    private val t = Thresholds(radiusM = 100, minAccuracyM = 30, dwellSeconds = 15, graceSeconds = 60)

    private fun good(distance: Int = 20, accuracy: Int = 8) =
        Observation(true, true, accuracy, distance, hasFix = true)

    private fun offSite(distance: Int = 400) = good(distance = distance)

    // ---------------------------------------------------------- trust rule

    @Test
    fun `all three checks passing is trusted`() {
        assertTrue(AttendanceLogic.trusted(good(), t))
    }

    @Test
    fun `right network but wrong router is not trusted`() {
        // The look-alike hotspot case: same SSID, different physical router.
        assertFalse(AttendanceLogic.trusted(good().copy(bssidAllowed = false), t))
    }

    @Test
    fun `right router but outside the fence is not trusted`() {
        assertFalse(AttendanceLogic.trusted(good(distance = 101), t))
    }

    @Test
    fun `a fix too fuzzy to place them is not trusted`() {
        assertFalse(AttendanceLogic.trusted(good(accuracy = 31), t))
    }

    @Test
    fun `no fix at all fails closed`() {
        assertFalse(AttendanceLogic.trusted(good().copy(hasFix = false), t))
    }

    @Test
    fun `exactly on the fence edge and accuracy limit still counts as inside`() {
        assertTrue(AttendanceLogic.trusted(good(distance = 100, accuracy = 30), t))
    }

    // ------------------------------------------------------------- bssid

    @Test
    fun `bssid match ignores case and padding`() {
        assertTrue(AttendanceLogic.bssidAllowed("3C:07:54:AA:1D:02", listOf(" 3c:07:54:aa:1d:02 ")))
    }

    @Test
    fun `the unavailable-permission sentinel is never a match`() {
        // Android returns this when NEARBY_WIFI_DEVICES is missing. If it were
        // ever allow-listed, anyone could clock in from anywhere.
        assertFalse(
            AttendanceLogic.bssidAllowed("02:00:00:00:00:00", listOf("02:00:00:00:00:00"))
        )
    }

    @Test
    fun `null or blank bssid is never a match`() {
        assertFalse(AttendanceLogic.bssidAllowed(null, listOf("3c:07:54:aa:1d:02")))
        assertFalse(AttendanceLogic.bssidAllowed("  ", listOf("3c:07:54:aa:1d:02")))
    }

    // ------------------------------------------------------------ arrival

    @Test
    fun `arriving does not clock in before the dwell period elapses`() {
        var s = MachineState()
        val d1 = AttendanceLogic.step(s, good(), t, 0L)
        assertEquals(Phase.PENDING_IN, d1.state.phase)
        assertEquals(Effect.NONE, d1.effect)

        s = d1.state
        val d2 = AttendanceLogic.step(s, good(), t, 14_000L)
        assertEquals(Phase.PENDING_IN, d2.state.phase)
        assertEquals(Effect.NONE, d2.effect)
        assertEquals(1, d2.dwellSecondsLeft)
    }

    @Test
    fun `arriving clocks in once the dwell period is satisfied`() {
        val pending = AttendanceLogic.step(MachineState(), good(), t, 0L).state
        val d = AttendanceLogic.step(pending, good(), t, 15_000L)
        assertEquals(Phase.IN, d.state.phase)
        assertEquals(Effect.OPEN_SESSION, d.effect)
    }

    @Test
    fun `walking past the building without settling never clocks in`() {
        // Passes briefly, then fails before dwell completes.
        val pending = AttendanceLogic.step(MachineState(), good(), t, 0L).state
        val d = AttendanceLogic.step(pending, offSite(), t, 5_000L)
        assertEquals(Phase.OUT, d.state.phase)
        assertEquals(Effect.NONE, d.effect)
    }

    @Test
    fun `the dwell clock restarts after a break rather than accumulating`() {
        // Someone hovering at the edge must not bank partial dwell time.
        val a = AttendanceLogic.step(MachineState(), good(), t, 0L).state
        val b = AttendanceLogic.step(a, offSite(), t, 10_000L).state   // resets
        val c = AttendanceLogic.step(b, good(), t, 11_000L)            // starts over
        assertEquals(Phase.PENDING_IN, c.state.phase)

        // 15s after the *original* arrival is only 4s into the new run.
        val d = AttendanceLogic.step(c.state, good(), t, 15_000L)
        assertEquals(Phase.PENDING_IN, d.state.phase)
        assertEquals(Effect.NONE, d.effect)

        val e = AttendanceLogic.step(d.state, good(), t, 26_000L)
        assertEquals(Effect.OPEN_SESSION, e.effect)
    }

    // ------------------------------------------------------------ leaving

    @Test
    fun `leaving starts a grace countdown rather than logging out at once`() {
        val inState = MachineState(Phase.IN)
        val d = AttendanceLogic.step(inState, offSite(), t, 0L)
        assertEquals(Phase.PENDING_OUT, d.state.phase)
        assertEquals(Effect.NONE, d.effect)
        assertEquals(60, d.graceSecondsLeft)
    }

    @Test
    fun `the grace countdown ticks down`() {
        val pendingOut = AttendanceLogic.step(MachineState(Phase.IN), offSite(), t, 0L).state
        val d = AttendanceLogic.step(pendingOut, offSite(), t, 45_000L)
        assertEquals(15, d.graceSecondsLeft)
        assertEquals(Effect.NONE, d.effect)
    }

    @Test
    fun `staying out for the whole grace period closes the session`() {
        val pendingOut = AttendanceLogic.step(MachineState(Phase.IN), offSite(), t, 0L).state
        val d = AttendanceLogic.step(pendingOut, offSite(), t, 60_000L)
        assertEquals(Phase.OUT, d.state.phase)
        assertEquals(Effect.CLOSE_SESSION, d.effect)
    }

    @Test
    fun `stepping out and coming back writes nothing at all`() {
        // The lunchtime-courtyard case. A logout here would be a payroll dispute.
        val pendingOut = AttendanceLogic.step(MachineState(Phase.IN), offSite(), t, 0L).state
        val back = AttendanceLogic.step(pendingOut, good(), t, 30_000L)
        assertEquals(Phase.IN, back.state.phase)
        assertEquals(Effect.NONE, back.effect)

        // And the grace clock must be forgotten, not resumed: leaving again
        // later gets a full fresh 60s.
        val leftAgain = AttendanceLogic.step(back.state, offSite(), t, 31_000L)
        assertEquals(60, leftAgain.graceSecondsLeft)
        assertEquals(Effect.NONE, leftAgain.effect)
    }

    @Test
    fun `a long gap between observations does not shorten the grace period`() {
        // Doze can swallow callbacks. Grace is measured from the timestamp of
        // the first failure, so a 10-minute gap still logs out correctly rather
        // than silently skipping the countdown.
        val pendingOut = AttendanceLogic.step(MachineState(Phase.IN), offSite(), t, 0L).state
        val d = AttendanceLogic.step(pendingOut, offSite(), t, 600_000L)
        assertEquals(Effect.CLOSE_SESSION, d.effect)
    }

    @Test
    fun `losing the router mid-shift is treated as leaving`() {
        // Still inside the fence, but the wi-fi dropped: the trust rule needs
        // all three, so the grace countdown starts.
        val d = AttendanceLogic.step(
            MachineState(Phase.IN), good().copy(bssidAllowed = false), t, 0L
        )
        assertEquals(Phase.PENDING_OUT, d.state.phase)
    }

    @Test
    fun `an already-closed day stays closed while off site`() {
        val d = AttendanceLogic.step(MachineState(Phase.OUT), offSite(), t, 0L)
        assertEquals(Phase.OUT, d.state.phase)
        assertEquals(Effect.NONE, d.effect)
    }

    // ------------------------------------------------------- full day run

    @Test
    fun `a whole ordinary shift opens exactly one session and closes it once`() {
        var s = MachineState()
        val effects = mutableListOf<Effect>()
        fun tick(o: Observation, at: Long) {
            val d = AttendanceLogic.step(s, o, t, at)
            s = d.state
            if (d.effect != Effect.NONE) effects += d.effect
        }

        val minute = 60_000L
        tick(good(), 0L)                       // arrives
        tick(good(), 20_000L)                  // dwell satisfied -> IN
        tick(good(), 60 * minute)              // mid-morning
        tick(offSite(120), 240 * minute)       // steps out for lunch
        tick(good(), 240 * minute + 30_000L)   // back inside grace -> no write
        tick(good(), 300 * minute)             // afternoon
        tick(offSite(), 540 * minute)          // goes home
        tick(offSite(), 541 * minute)          // grace expires -> OUT

        assertEquals(listOf(Effect.OPEN_SESSION, Effect.CLOSE_SESSION), effects)
        assertEquals(Phase.OUT, s.phase)
    }
}

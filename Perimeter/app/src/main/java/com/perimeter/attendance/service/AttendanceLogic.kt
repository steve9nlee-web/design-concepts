package com.perimeter.attendance.service

/**
 * The decision half of the attendance rule, with every Android dependency
 * removed on purpose.
 *
 * This is the code that decides whether someone gets paid for a shift, so it is
 * the code most worth being able to test. Keeping it free of Context, Location
 * and WifiManager means it compiles and runs on a plain JVM — see
 * `app/src/test/java/.../AttendanceLogicTest.kt`. AttendanceService is then a
 * thin shell that reads sensors, calls [step], and acts on the answer.
 */

enum class Phase { OUT, PENDING_IN, IN, PENDING_OUT }

/** Everything [step] needs to know about one sensor reading. */
data class Observation(
    val ssidMatches: Boolean,
    val bssidAllowed: Boolean,
    val accuracyM: Int,
    val distanceM: Int,
    val hasFix: Boolean
)

data class Thresholds(
    val radiusM: Int,
    val minAccuracyM: Int,
    val dwellSeconds: Int,
    val graceSeconds: Int
)

/**
 * Where the machine is, plus when the current run of passes or failures began.
 * Timestamps rather than countdowns: a missed callback or a doze window can
 * delay the next observation, but it cannot shorten a dwell or a grace period.
 *
 * The two marks are nullable rather than using 0 as "unset" — 0 is a legal
 * epoch timestamp, and conflating the two made a run that began at 0 restart
 * itself on every observation, so a grace period would never expire.
 */
data class MachineState(
    val phase: Phase = Phase.OUT,
    val satisfiedSince: Long? = null,
    val violatedSince: Long? = null
)

/** What the caller should do as a result of [step]. */
enum class Effect { NONE, OPEN_SESSION, CLOSE_SESSION }

data class Decision(
    val state: MachineState,
    val effect: Effect,
    val graceSecondsLeft: Int = 0,
    val dwellSecondsLeft: Int = 0
)

object AttendanceLogic {

    /**
     * The trust rule from HANDOFF.md section 1. All three must hold.
     *
     * SSID alone is trusted for nothing — it is a string anyone can broadcast.
     * The BSSID pins the physical router; the GPS fix defeats a look-alike
     * hotspot standing off-site. A missing fix fails closed rather than being
     * treated as "probably fine".
     */
    fun trusted(o: Observation, t: Thresholds): Boolean =
        o.ssidMatches &&
            o.bssidAllowed &&
            o.hasFix &&
            o.accuracyM <= t.minAccuracyM &&
            o.distanceM <= t.radiusM

    /**
     * Advance the machine by one observation taken at [now] (epoch millis).
     *
     *   OUT ─pass for dwellSeconds──▶ IN            OPEN_SESSION
     *   IN  ─fail for graceSeconds──▶ OUT           CLOSE_SESSION
     *   IN  ─fail, then pass again──▶ IN            nothing written
     */
    fun step(prev: MachineState, o: Observation, t: Thresholds, now: Long): Decision {
        val ok = trusted(o, t)

        if (ok) {
            val satisfiedSince = prev.satisfiedSince ?: now
            return when (prev.phase) {
                Phase.OUT, Phase.PENDING_IN -> {
                    val held = now - satisfiedSince
                    if (held >= t.dwellSeconds * 1000L) {
                        Decision(MachineState(Phase.IN, satisfiedSince, null), Effect.OPEN_SESSION)
                    } else {
                        Decision(
                            MachineState(Phase.PENDING_IN, satisfiedSince, null),
                            Effect.NONE,
                            dwellSecondsLeft = secondsLeft(t.dwellSeconds, held)
                        )
                    }
                }
                // Re-entry inside the grace window cancels the pending logout.
                // Nothing is written — the shift was never actually broken, and
                // the grace mark is dropped so leaving again gets a full period.
                Phase.PENDING_OUT -> Decision(MachineState(Phase.IN, satisfiedSince, null), Effect.NONE)
                Phase.IN -> Decision(MachineState(Phase.IN, satisfiedSince, null), Effect.NONE)
            }
        }

        val violatedSince = prev.violatedSince ?: now
        return when (prev.phase) {
            Phase.IN, Phase.PENDING_OUT -> {
                val held = now - violatedSince
                if (held >= t.graceSeconds * 1000L) {
                    Decision(MachineState(Phase.OUT, null, null), Effect.CLOSE_SESSION)
                } else {
                    Decision(
                        MachineState(Phase.PENDING_OUT, null, violatedSince),
                        Effect.NONE,
                        graceSecondsLeft = secondsLeft(t.graceSeconds, held)
                    )
                }
            }
            // A login that was still being verified simply never happened.
            Phase.PENDING_IN -> Decision(MachineState(Phase.OUT, null, violatedSince), Effect.NONE)
            Phase.OUT -> Decision(MachineState(Phase.OUT, null, violatedSince), Effect.NONE)
        }
    }

    private fun secondsLeft(totalSeconds: Int, heldMs: Long): Int {
        val left = ((totalSeconds * 1000L - heldMs) / 1000L).toInt()
        return if (left < 0) 0 else left
    }

    /**
     * Android hands back this sentinel BSSID when NEARBY_WIFI_DEVICES is missing
     * or the location toggle is off. Treating it as a match would let anyone
     * with a same-named hotspot clock in, so it can never be allowed.
     */
    const val UNAVAILABLE_BSSID = "02:00:00:00:00:00"

    fun bssidAllowed(bssid: String?, allowList: List<String>): Boolean {
        if (bssid.isNullOrBlank()) return false
        val b = bssid.trim().lowercase()
        if (b == UNAVAILABLE_BSSID) return false
        return allowList.any { it.trim().lowercase() == b }
    }
}

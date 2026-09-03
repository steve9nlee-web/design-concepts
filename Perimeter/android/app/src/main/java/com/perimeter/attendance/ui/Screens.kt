package com.perimeter.attendance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.perimeter.attendance.config.SiteConfig
import com.perimeter.attendance.data.Session
import com.perimeter.attendance.service.AttendanceService
import com.perimeter.attendance.service.TrustResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun hhmm(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

private fun dur(from: Long, to: Long): String {
    val mins = ((to - from) / 60000L).toInt()
    return "${mins / 60}h ${mins % 60}m"
}

@Composable
private fun Eyebrow(text: String, color: Color = P.Faint) {
    Text(
        text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace, letterSpacing = 1.4.sp
    )
}

@Composable
private fun Card(bg: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bg).padding(20.dp),
        content = content
    )
}

@Composable
private fun OutlinedCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(2.dp, P.Ink, RoundedCornerShape(22.dp))
            .padding(16.dp),
        content = content
    )
}

// ---------------------------------------------------------------- Status

@Composable
fun StatusScreen(state: AttendanceService.UiState, cfg: SiteConfig, onStay: () -> Unit, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Hi, ${cfg.staffName.substringBefore(' ')}",
            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = P.Ink
        )

        when (state.phase) {
            AttendanceService.Phase.IN -> Card(P.Green) {
                Eyebrow("LOGGED IN · AUTO", P.MintText)
                Spacer(Modifier.height(12.dp))
                Text("You're on site", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEAFFF4))
                Spacer(Modifier.height(6.dp))
                Text(cfg.siteName, fontSize = 15.sp, color = P.MintText)
                state.sessionStart?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Since ${hhmm(it)} · ${dur(it, System.currentTimeMillis())}",
                        fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = Color.White
                    )
                }
            }

            AttendanceService.Phase.PENDING_OUT -> Card(P.Orange) {
                Eyebrow("LEFT THE FENCE · HOLDING", P.Cream)
                Spacer(Modifier.height(12.dp))
                Text(
                    "${state.graceSecondsLeft}s",
                    fontSize = 44.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Text("Still here?", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    state.trust?.reason ?: "Outside the fence",
                    fontSize = 14.sp, color = Color(0xFFFFD9BD)
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(P.Cream).clickable { onStay() }.padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("I'm still on site", color = P.Ink, fontWeight = FontWeight.SemiBold) }
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .background(Color(0x38000000)).clickable { onLogout() }
                            .padding(horizontal = 18.dp, vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Log out", color = P.Cream, fontWeight = FontWeight.SemiBold) }
                }
            }

            AttendanceService.Phase.PENDING_IN -> Card(P.Ink) {
                Eyebrow("VERIFYING", Color(0xFFFF9D5C))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Checking you're really here",
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = P.Bg
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "All three checks must hold for ${cfg.dwellSeconds}s. ${state.dwellSecondsLeft}s to go.",
                    fontSize = 14.sp, color = P.OnDark
                )
            }

            AttendanceService.Phase.OUT -> OutlinedCard {
                Eyebrow("NOT ON SITE")
                Spacer(Modifier.height(10.dp))
                Text("Day closed", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = P.Ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    state.trust?.reason ?: "Waiting for the site network",
                    fontSize = 14.sp, color = P.Muted
                )
            }
        }

        if (!state.locationAvailable) {
            Card(P.Red) {
                Eyebrow("CAN'T VERIFY", Color(0xFFFFC9C0))
                Spacer(Modifier.height(10.dp))
                Text("Location is off", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Wi-fi alone can be faked. Turn location back on and your session resumes.",
                    fontSize = 14.sp, color = Color(0xFFFFD9D3)
                )
            }
        }

        // Diagnostics stay visible by default: staff trust an automatic
        // timesheet only when they can see why it decided what it decided.
        state.trust?.let { t -> DiagnosticsCard(t, cfg) }
    }
}

@Composable
private fun DiagnosticsCard(t: TrustResult, cfg: SiteConfig) {
    OutlinedCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Eyebrow("SIGNAL CHECK", P.Ink)
            Eyebrow(
                if (t.trusted) "VERIFIED" else "MISMATCH",
                if (t.trusted) P.Green else P.Red
            )
        }
        Spacer(Modifier.height(12.dp))
        DiagRow("Network", t.ssid, t.ssidOk)
        DiagRow("Router ID", t.bssid, t.bssidOk)
        DiagRow("Accuracy", if (t.accuracyM >= 0) "±${t.accuracyM} m" else "—", t.gpsOk)
        DiagRow(
            "Distance",
            if (t.distanceM >= 0) "${t.distanceM} m of ${cfg.radiusM} m" else "—",
            t.inFence
        )
    }
}

@Composable
private fun DiagRow(k: String, v: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(k, fontSize = 12.sp, color = P.Muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = P.Ink)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(7.dp).clip(RoundedCornerShape(7.dp))
                    .background(if (ok) P.Green else P.Red)
            )
        }
    }
}

// ------------------------------------------------------------------- Log

@Composable
fun LogScreen(sessions: List<Session>) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Your log", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = P.Ink)
        if (sessions.isEmpty()) {
            Text("No sessions recorded yet.", color = P.Muted, fontSize = 14.sp)
        }
        sessions.forEach { s ->
            OutlinedCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(s.loginAt)),
                        fontWeight = FontWeight.SemiBold, color = P.Ink
                    )
                    Text(
                        s.logoutAt?.let { dur(s.loginAt, it) } ?: "—",
                        fontFamily = FontFamily.Monospace, color = P.Ink
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${hhmm(s.loginAt)} → ${s.logoutAt?.let { hhmm(it) } ?: "open"}",
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = P.Muted
                    )
                    Text(
                        s.flag, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (s.flag) {
                            "OK" -> P.Green
                            "OPEN" -> P.Red
                            else -> P.Orange
                        }
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------- Sheet

@Composable
fun SheetScreen(pending: Int, isAdmin: Boolean, cfg: SiteConfig, lastError: String?, onSync: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Sheet sync", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = P.Ink)

        Card(if (pending == 0) P.Green else P.Orange) {
            Eyebrow(if (pending == 0) "CONNECTED" else "QUEUED", Color(0xCCFFFFFF))
            Spacer(Modifier.height(10.dp))
            Text(
                if (pending == 0) "Sheet is up to date"
                else "$pending row${if (pending == 1) "" else "s"} waiting",
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (pending == 0)
                    "Written by the attendance service, not by you."
                else
                    "Rows are held on the phone and written in order once a connection returns. Nothing is lost.",
                fontSize = 13.sp, color = Color(0xE6FFFFFF)
            )
            lastError?.let {
                Spacer(Modifier.height(8.dp))
                Text("Last error: $it", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFFFE0D6))
            }

            // Admin-only: the folder and endpoint are not staff business.
            if (isAdmin) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "endpoint  ${cfg.endpointUrl.ifBlank { "not set" }}",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xE6FFFFFF)
                )
                Text(
                    "site      ${cfg.siteId}",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xE6FFFFFF)
                )
            } else {
                Spacer(Modifier.height(14.dp))
                Text(
                    "The file location and Drive access are held by your administrator.",
                    fontSize = 12.sp, color = Color(0xE6FFFFFF)
                )
            }

            if (isAdmin || pending > 0) {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(P.Cream).clickable { onSync() }.padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Retry now", color = P.Ink, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ---------------------------------------------------------------- Keypad

@Composable
fun KeypadScreen(
    entered: Int,
    pinLength: Int,
    message: String,
    isError: Boolean,
    cooling: Boolean,
    onKey: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Eyebrow("RESTRICTED")
        Text("Admin only", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = P.Ink)

        Card(P.Ink) {
            Text(
                "Fence radius, grace period and the router allow-list decide what your timesheet says, so only an administrator can open them.",
                fontSize = 14.sp, color = P.OnDark
            )
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pinLength) { i ->
                    Box(
                        Modifier.padding(horizontal = 7.dp).size(16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (i < entered) P.Orange else Color(0xFF4A3F36))
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                message,
                fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.2.sp,
                color = if (isError || cooling) Color(0xFFFF9D5C) else P.Muted,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "<")
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (cooling) P.Line else Color.White)
                                .border(
                                    2.dp,
                                    if (cooling) Color(0xFFDCD3C6) else P.Ink,
                                    RoundedCornerShape(18.dp)
                                )
                                .clickable(enabled = !cooling) { onKey(k) }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                k, fontSize = 21.sp, fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = if (cooling) P.Faint else P.Ink
                            )
                        }
                    }
                }
            }
        }

        Text(
            "The PIN is set by your administrator and is checked on this phone. Five wrong tries lock the keypad for a minute — and the count survives a force-quit.",
            fontSize = 12.sp, color = P.Muted
        )
    }
}

// ----------------------------------------------------------------- Rules

@Composable
fun RulesScreen(cfg: SiteConfig, idleLeft: Int, onLock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Eyebrow("ADMIN")
        Text("Site & rules", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = P.Ink)

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(P.Orange)
                .padding(start = 15.dp, end = 11.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ADMIN SESSION · ${idleLeft / 60}:${(idleLeft % 60).toString().padStart(2, '0')}",
                Modifier.weight(1f),
                fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold, color = P.Cream
            )
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(P.Ink)
                    .clickable { onLock() }.padding(horizontal = 15.dp, vertical = 10.dp)
            ) { Text("Lock", color = P.Bg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }

        OutlinedCard {
            SettingRow("Site", cfg.siteName)
            SettingRow("Fence radius", "${cfg.radiusM} m")
            SettingRow("Grace before logout", "${cfg.graceSeconds} s")
            SettingRow("Dwell before login", "${cfg.dwellSeconds} s")
            SettingRow("Min GPS accuracy", "${cfg.minAccuracyM} m")
            SettingRow("Allowed routers", "${cfg.allowedBssids.size}")
            SettingRow("Network", cfg.ssid)
            SettingRow("Staff", "${cfg.staffName} · ${cfg.staffId}")
        }

        Text(
            "Changing the fence or router list only affects rows written from now on. Past rows stay as recorded.",
            fontSize = 12.sp, color = P.Muted
        )
        Text(
            "These settings live on this handset. Until a server owns them, a rooted phone could change what gets recorded — see HANDOFF.md section 2.",
            fontSize = 12.sp, color = P.Red
        )
    }
}

@Composable
private fun SettingRow(k: String, v: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(k, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = P.Ink)
        Text(v, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = P.Muted)
    }
}

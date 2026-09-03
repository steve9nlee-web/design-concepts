package com.perimeter.attendance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.perimeter.attendance.config.AdminPin
import com.perimeter.attendance.config.SiteConfig
import com.perimeter.attendance.data.SessionStore
import com.perimeter.attendance.service.AttendanceService
import com.perimeter.attendance.ui.KeypadScreen
import com.perimeter.attendance.ui.LogScreen
import com.perimeter.attendance.ui.P
import com.perimeter.attendance.ui.PerimeterTheme
import com.perimeter.attendance.ui.RulesScreen
import com.perimeter.attendance.ui.SheetScreen
import com.perimeter.attendance.ui.StatusScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val requestFine = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            // Background location has to be a second, separate ask with its own
            // explanation. A single combined prompt gets denied.
            requestBackground()
            AttendanceService.start(this)
        }
    }

    private val requestBg = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, the service still runs while the app is open */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askPermissions()
        setContent { PerimeterTheme { AppRoot() } }
    }

    private fun askPermissions() {
        val wanted = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted += Manifest.permission.NEARBY_WIFI_DEVICES
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            requestBackground()
            AttendanceService.start(this)
        } else {
            requestFine.launch(missing.toTypedArray())
        }
    }

    private fun requestBackground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestBg.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
}

private enum class Tab { STATUS, LOG, SHEET, KEYPAD, RULES }

@Composable
private fun AppRoot() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { SessionStore(ctx) }
    val state by AttendanceService.state.collectAsState()

    var cfg by remember { mutableStateOf(SiteConfig.load(ctx)) }
    var tab by remember { mutableStateOf(Tab.STATUS) }
    var isAdmin by remember { mutableStateOf(false) }
    var idleLeft by remember { mutableIntStateOf(0) }

    var entry by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var cooldownLeft by remember { mutableIntStateOf(0) }
    var sessions by remember { mutableStateOf(store.recent()) }
    var pending by remember { mutableIntStateOf(store.pending().size) }

    val pinLength = remember { AdminPin.pinLength(ctx) }

    // One ticker drives the admin idle countdown, the keypad cooldown, and the
    // list refresh. Auto-lock mirrors the design: leaving it open on a staff
    // phone is the whole thing this feature exists to prevent.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            sessions = store.recent()
            pending = store.pending().size
            cooldownLeft = (AdminPin.cooldownRemainingMs(ctx) / 1000L).toInt()
            if (isAdmin) {
                idleLeft -= 1
                if (idleLeft <= 0) {
                    isAdmin = false
                    if (tab == Tab.RULES) tab = Tab.STATUS
                }
            }
        }
    }

    fun lock() {
        isAdmin = false
        idleLeft = 0
        entry = ""
        pinError = false
        if (tab == Tab.RULES) tab = Tab.STATUS
    }

    fun onKey(k: String) {
        if (cooldownLeft > 0) return
        when (k) {
            "C" -> { entry = ""; pinError = false }
            "<" -> { entry = entry.dropLast(1); pinError = false }
            else -> {
                if (entry.length >= pinLength) return
                val next = entry + k
                if (next.length < pinLength) {
                    entry = next
                    pinError = false
                } else {
                    if (AdminPin.verify(ctx, next)) {
                        isAdmin = true
                        idleLeft = cfg.adminIdleSeconds
                        cfg = SiteConfig.load(ctx)
                        tab = Tab.RULES
                        entry = ""
                        pinError = false
                    } else {
                        entry = ""
                        pinError = true
                        cooldownLeft = (AdminPin.cooldownRemainingMs(ctx) / 1000L).toInt()
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(P.Bg)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.STATUS -> StatusScreen(
                    state, cfg,
                    onStay = { AttendanceService.send(ctx, AttendanceService.ACTION_STAY) },
                    onLogout = { AttendanceService.send(ctx, AttendanceService.ACTION_LOGOUT_NOW) }
                )
                Tab.LOG -> LogScreen(sessions)
                Tab.SHEET -> SheetScreen(
                    pending, isAdmin, cfg, state.lastError,
                    onSync = { AttendanceService.send(ctx, AttendanceService.ACTION_SYNC) }
                )
                Tab.KEYPAD -> KeypadScreen(
                    entered = entry.length,
                    pinLength = pinLength,
                    message = when {
                        cooldownLeft > 0 -> "TOO MANY TRIES · ${cooldownLeft}S"
                        pinError -> "WRONG PIN · ${AdminPin.MAX_TRIES - AdminPin.triesUsed(ctx)} LEFT"
                        else -> "ENTER THE $pinLength-DIGIT ADMIN PIN"
                    },
                    isError = pinError,
                    cooling = cooldownLeft > 0,
                    onKey = { k -> onKey(k) }
                )
                Tab.RULES -> RulesScreen(cfg, idleLeft, onLock = { lock() })
            }
        }
        TabBar(
            tab = tab,
            isAdmin = isAdmin,
            onTab = { tab = it },
            onLockChip = {
                if (isAdmin) lock() else { entry = ""; pinError = false; tab = Tab.KEYPAD }
            }
        )
    }
}

@Composable
private fun TabBar(tab: Tab, isAdmin: Boolean, onTab: (Tab) -> Unit, onLockChip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabButton("Status", tab == Tab.STATUS, Modifier.weight(1f)) { onTab(Tab.STATUS) }
        TabButton("Log", tab == Tab.LOG, Modifier.weight(1f)) { onTab(Tab.LOG) }
        TabButton("Sheet", tab == Tab.SHEET, Modifier.weight(1f)) { onTab(Tab.SHEET) }
        // The Rules tab does not exist for staff. It appears only once unlocked.
        if (isAdmin) {
            TabButton("Rules", tab == Tab.RULES, Modifier.weight(1f)) { onTab(Tab.RULES) }
        }
        LockChip(isAdmin, tab == Tab.KEYPAD, onLockChip)
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) P.Ink else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) P.Bg else P.Faint
        )
    }
}

/**
 * Visible to everyone, but it leads to a keypad rather than to settings. A
 * hidden gesture would mean an admin can't get in on a staff phone without
 * being told the trick.
 */
@Composable
private fun LockChip(isAdmin: Boolean, onKeypad: Boolean, onClick: () -> Unit) {
    val fg = when {
        isAdmin -> P.Cream
        onKeypad -> P.Bg
        else -> P.Faint
    }
    Box(
        Modifier.width(52.dp).clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isAdmin -> P.Orange
                    onKeypad -> P.Ink
                    else -> Color.Transparent
                }
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(width = 16.dp, height = 11.dp)
                    .clip(RoundedCornerShape(3.dp)).background(fg)
            )
            Spacer(Modifier.height(5.dp))
            Text(
                if (isAdmin) "LOCK" else "ADMIN",
                fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold, color = fg
            )
        }
    }
}

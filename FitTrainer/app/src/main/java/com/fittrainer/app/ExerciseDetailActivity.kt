package com.fittrainer.app

import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fittrainer.app.data.ExType
import com.fittrainer.app.data.ExerciseDb
import com.fittrainer.app.data.RecordStore
import com.fittrainer.app.data.WorkoutRecord
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class ExerciseDetailActivity : AppCompatActivity() {

    private var timer: CountDownTimer? = null
    private var toneGen: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val e = ExerciseDb.byId(intent.getStringExtra("id") ?: "") ?: run { finish(); return }
        val cat = Prefs.ageCategory(this)
        val p = ExerciseDb.prescriptionFor(e, cat)
        val unit = if (e.type == ExType.TIME) "sec" else "reps"
        title = e.name
        toneGen = try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) } catch (ex: Exception) { null }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        fun section(titleText: String): TextView = TextView(this).apply {
            text = titleText
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        }
        fun body(t: String): TextView = TextView(this).apply { text = t; textSize = 14f }
        fun margin(top: Int = 12) = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }

        // Your plan
        root.addView(section("Your plan — ${cat.label} (${cat.range} yrs)"))
        root.addView(body("${p.sets} sets × ${p.amount} $unit, rest ${p.restSeconds}s between sets"), margin(4))

        // Method
        root.addView(section("How to do it"), margin())
        e.method.forEachIndexed { i, step ->
            root.addView(body("${i + 1}. $step"), margin(4))
        }

        // Age guidance
        root.addView(section("Guidance for your age group"), margin())
        root.addView(body(p.ageNote), margin(4))

        // Goal
        root.addView(section("🎯 What to achieve"), margin())
        root.addView(body(p.goalText), margin(4))

        // Timer card
        val timerCard = MaterialCardView(this).apply {
            radius = dp(12).toFloat()
            cardElevation = dp(2).toFloat()
            setContentPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val timerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val timerLabel = TextView(this).apply {
            text = if (e.type == ExType.TIME) "Exercise timer (${p.amount}s)" else "Rest timer (${p.restSeconds}s)"
            textSize = 14f
        }
        val timerView = TextView(this).apply {
            text = format(if (e.type == ExType.TIME) p.amount else p.restSeconds)
            textSize = 44f
            setTypeface(typeface, Typeface.BOLD)
        }
        val startBtn = MaterialButton(this).apply { text = "Start" }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val exBtn = MaterialButton(this).apply { text = "Exercise ${if (e.type == ExType.TIME) p.amount else 45}s" }
        val restBtn = MaterialButton(this).apply { text = "Rest ${p.restSeconds}s" }

        var duration = if (e.type == ExType.TIME) p.amount else p.restSeconds

        fun arm(seconds: Int, label: String) {
            timer?.cancel()
            duration = seconds
            timerLabel.text = label
            timerView.text = format(seconds)
            startBtn.text = "Start"
        }

        exBtn.setOnClickListener { arm(if (e.type == ExType.TIME) p.amount else 45, "Exercise timer") }
        restBtn.setOnClickListener { arm(p.restSeconds, "Rest timer") }

        startBtn.setOnClickListener {
            if (startBtn.text == "Stop") {
                timer?.cancel()
                startBtn.text = "Start"
                timerView.text = format(duration)
                return@setOnClickListener
            }
            startBtn.text = "Stop"
            timer = object : CountDownTimer(duration * 1000L, 250) {
                override fun onTick(ms: Long) {
                    timerView.text = format(((ms + 999) / 1000).toInt())
                }
                override fun onFinish() {
                    timerView.text = "Done!"
                    startBtn.text = "Start"
                    toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
                }
            }.start()
        }

        timerCol.addView(timerLabel)
        timerCol.addView(timerView)
        row.addView(exBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
        row.addView(restBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(4) })
        timerCol.addView(row, margin(8))
        timerCol.addView(startBtn, margin(4))
        timerCard.addView(timerCol)
        root.addView(timerCard, margin(16))

        // Log a workout
        root.addView(section("Log this workout"), margin(16))
        val setsIn = EditText(this).apply {
            hint = "Sets completed"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(p.sets.toString())
        }
        val amountIn = EditText(this).apply {
            hint = if (e.type == ExType.TIME) "Seconds per set" else "Reps per set"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(p.amount.toString())
        }
        val noteIn = EditText(this).apply { hint = "Note (optional)" }
        root.addView(setsIn, margin(4))
        root.addView(amountIn, margin(4))
        root.addView(noteIn, margin(4))

        root.addView(MaterialButton(this).apply {
            text = "✅  Save record"
            setOnClickListener {
                val sets = setsIn.text.toString().toIntOrNull()
                val amount = amountIn.text.toString().toIntOrNull()
                if (sets == null || amount == null || sets <= 0 || amount <= 0) {
                    Toast.makeText(context, "Enter valid sets and $unit", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                RecordStore.add(context, WorkoutRecord(
                    System.currentTimeMillis(),
                    e.area.label, e.name, "${cat.label} (${cat.range})",
                    e.type.name, sets, amount, sets * amount,
                    noteIn.text.toString().trim()
                ))
                Toast.makeText(context, "Workout saved 💪", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, margin(12))
    }

    private fun format(totalSec: Int): String = "%d:%02d".format(totalSec / 60, totalSec % 60)

    override fun onDestroy() {
        timer?.cancel()
        toneGen?.release()
        super.onDestroy()
    }
}

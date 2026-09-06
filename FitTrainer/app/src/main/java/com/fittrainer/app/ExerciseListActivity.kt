package com.fittrainer.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fittrainer.app.data.Area
import com.fittrainer.app.data.ExType
import com.fittrainer.app.data.ExerciseDb
import com.google.android.material.card.MaterialCardView

class ExerciseListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val area = Area.valueOf(intent.getStringExtra("area") ?: Area.LEGS.name)
        title = "${area.emoji} ${area.label}"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        val cat = Prefs.ageCategory(this)
        root.addView(TextView(this).apply {
            text = "Plan for ${cat.label} (${cat.range} yrs) — tap an exercise for the full method, timer and goal."
            textSize = 14f
        })

        for (e in ExerciseDb.byArea(area)) {
            val p = ExerciseDb.prescriptionFor(e, cat)
            val unit = if (e.type == ExType.TIME) "sec" else "reps"
            val card = MaterialCardView(this).apply {
                radius = dp(12).toFloat()
                cardElevation = dp(2).toFloat()
                setContentPadding(dp(16), dp(12), dp(16), dp(12))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(
                        Intent(this@ExerciseListActivity, ExerciseDetailActivity::class.java)
                            .putExtra("id", e.id)
                    )
                }
            }
            val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            inner.addView(TextView(this).apply {
                text = e.name
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
            })
            inner.addView(TextView(this).apply {
                text = "${p.sets} sets × ${p.amount} $unit • rest ${p.restSeconds}s"
                textSize = 14f
            })
            card.addView(inner)
            root.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) })
        }
    }
}

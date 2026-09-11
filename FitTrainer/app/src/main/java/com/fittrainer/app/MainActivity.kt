package com.fittrainer.app

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fittrainer.app.data.AgeCategory
import com.fittrainer.app.data.Area
import com.fittrainer.app.data.ExerciseDb
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

object Prefs {
    private const val NAME = "fittrainer_prefs"
    private const val KEY_AGE = "age_category"

    fun ageCategory(ctx: Context): AgeCategory =
        AgeCategory.fromName(
            ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_AGE, null)
        )

    fun setAgeCategory(ctx: Context, cat: AgeCategory) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AGE, cat.name).apply()
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "Pick your age group — every training method, set, rep and goal below adapts to it."
            textSize = 15f
        })

        val spinner = Spinner(this)
        val cats = AgeCategory.entries
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            cats.map { "${it.label} (${it.range} yrs)" }
        )
        spinner.setSelection(cats.indexOf(Prefs.ageCategory(this)))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                Prefs.setAgeCategory(this@MainActivity, cats[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        root.addView(spinner, lp(topMargin = dp(8)))

        root.addView(TextView(this).apply {
            text = "Training areas"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }, lp(topMargin = dp(16)))

        for (area in Area.entries) {
            val card = MaterialCardView(this).apply {
                radius = dp(12).toFloat()
                cardElevation = dp(2).toFloat()
                setContentPadding(dp(16), dp(12), dp(16), dp(12))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(
                        Intent(this@MainActivity, ExerciseListActivity::class.java)
                            .putExtra("area", area.name)
                    )
                }
            }
            val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val count = ExerciseDb.byArea(area).size
            inner.addView(TextView(this).apply {
                text = "${area.emoji}  ${area.label}"
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
            })
            inner.addView(TextView(this).apply {
                text = "${area.description} • $count exercises"
                textSize = 13f
            })
            card.addView(inner)
            root.addView(card, lp(topMargin = dp(10)))
        }

        root.addView(MaterialButton(this).apply {
            text = "📊  Workout records & Excel export"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, HistoryActivity::class.java))
            }
        }, lp(topMargin = dp(20)))

        root.addView(TextView(this).apply {
            text = "Guidelines are based on WHO physical-activity recommendations per age group. " +
                    "Consult a doctor before starting a new exercise program, especially over 65 " +
                    "or with existing health conditions."
            textSize = 12f
            gravity = Gravity.CENTER
            alpha = 0.7f
        }, lp(topMargin = dp(16), bottomMargin = dp(16)))

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun lp(topMargin: Int = 0, bottomMargin: Int = 0) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = topMargin
            this.bottomMargin = bottomMargin
        }
}

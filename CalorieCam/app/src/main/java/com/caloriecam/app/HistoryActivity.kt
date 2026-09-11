package com.caloriecam.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.caloriecam.app.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val meals = MealDb(this).allMeals()
        if (meals.isEmpty()) {
            binding.textHistory.text = "No meals logged yet."
            return
        }

        val sb = StringBuilder()
        var currentDate = ""
        var dayTotal = 0
        for (meal in meals) {
            if (meal.date != currentDate) {
                if (currentDate.isNotEmpty()) sb.append("   Total: $dayTotal kcal\n\n")
                currentDate = meal.date
                dayTotal = 0
                sb.append("📅 ${meal.date}\n")
            }
            dayTotal += meal.calories
            val syncMark = if (meal.synced) "✔" else "⏳"
            sb.append("   ${meal.time}  ${meal.foodName} — ${meal.calories} kcal $syncMark\n")
        }
        sb.append("   Total: $dayTotal kcal\n")
        binding.textHistory.text = sb.toString()
    }
}

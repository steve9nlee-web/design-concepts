package com.caloriecam.app

import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Generates daily diet and exercise suggestions from the profile and today's intake.
 * Rule-based so it works offline; rotates content by day of year.
 */
object SuggestionEngine {

    private val exercisesLose = listOf(
        "Brisk walk 45 min (~250 kcal)",
        "Jog 30 min (~300 kcal)",
        "Cycling 40 min (~350 kcal)",
        "Swimming 30 min (~300 kcal)",
        "HIIT session 20 min (~250 kcal)",
        "Jump rope 15 min + walk 20 min (~280 kcal)",
        "Stair climbing 20 min (~220 kcal)"
    )

    private val exercisesGain = listOf(
        "Full-body strength training 45 min",
        "Push day: chest, shoulders, triceps 40 min",
        "Pull day: back and biceps 40 min",
        "Leg day: squats, lunges, calf raises 45 min",
        "Core and mobility work 30 min",
        "Resistance bands full body 35 min",
        "Bodyweight circuit: push-ups, rows, squats 30 min"
    )

    private val exercisesMaintain = listOf(
        "Walk 30 min + light stretching",
        "Yoga 30 min",
        "Casual cycling 30 min",
        "Light swim 25 min",
        "Bodyweight circuit 20 min",
        "Dance or aerobics 30 min",
        "Hike or long walk 45 min"
    )

    private val dietTipsLose = listOf(
        "Fill half your plate with vegetables before adding anything else.",
        "Swap sugary drinks for water or unsweetened tea today.",
        "Eat protein first at each meal — it keeps you full longer.",
        "Use a smaller plate; portions look bigger and satisfy more.",
        "Avoid eating within 2–3 hours of bedtime.",
        "Choose grilled or steamed over fried today.",
        "Plan tomorrow's meals tonight so hunger doesn't decide for you."
    )

    private val dietTipsGain = listOf(
        "Add a calorie-dense snack: nuts, peanut butter, or a smoothie.",
        "Eat every 3–4 hours; don't skip meals.",
        "Add healthy fats: avocado, olive oil, or nut butter to meals.",
        "Drink a glass of milk or a protein shake with meals.",
        "Aim for 1.6–2 g of protein per kg of body weight today.",
        "Choose whole-grain carbs to fuel your workouts.",
        "Add an extra portion of rice or potatoes at dinner."
    )

    private val dietTipsMaintain = listOf(
        "Keep a consistent meal schedule today.",
        "Aim for balanced plates: ½ veg, ¼ protein, ¼ whole grains.",
        "Stay hydrated: about 8 glasses of water.",
        "Include at least 2 servings of fruit today.",
        "Watch hidden calories in sauces and dressings.",
        "Eat slowly and stop at 80% full.",
        "Include a fermented food for gut health."
    )

    data class Suggestions(
        val headline: String,
        val dietTip: String,
        val exerciseTip: String,
        val statusLine: String
    )

    fun forToday(profile: UserProfile, consumedToday: Int): Suggestions {
        val target = profile.dailyCalorieTarget()
        val remaining = target - consumedToday
        val toGoal = profile.targetWeightKg - profile.weightKg
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

        val losing = toGoal < -0.5
        val gaining = toGoal > 0.5

        val (diet, exercise) = when {
            losing -> dietTipsLose[day % dietTipsLose.size] to exercisesLose[day % exercisesLose.size]
            gaining -> dietTipsGain[day % dietTipsGain.size] to exercisesGain[day % exercisesGain.size]
            else -> dietTipsMaintain[day % dietTipsMaintain.size] to exercisesMaintain[day % exercisesMaintain.size]
        }

        val headline = when {
            losing -> "Goal: lose ${abs(toGoal).roundToInt()} kg — stay under $target kcal/day"
            gaining -> "Goal: gain ${abs(toGoal).roundToInt()} kg — reach $target kcal/day"
            else -> "Goal: maintain ${profile.weightKg.roundToInt()} kg — around $target kcal/day"
        }

        val statusLine = when {
            remaining > 0 -> "$remaining kcal left today"
            remaining == 0 -> "You hit your target exactly."
            losing -> "${-remaining} kcal over target — add ~${(-remaining / 8)} min of brisk walking to offset"
            else -> "${-remaining} kcal over today's target"
        }

        return Suggestions(headline, diet, exercise, statusLine)
    }
}

package com.caloriecam.app

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.caloriecam.app.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var profile: UserProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        profile = UserProfile(this)

        binding.spinnerActivity.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Sedentary (little exercise)", "Light (1-3 days/week)",
                "Moderate (3-5 days/week)", "Active (6-7 days/week)")
        )
        binding.spinnerSex.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, listOf("Male", "Female")
        )

        // Pre-fill existing values
        if (profile.isComplete) {
            binding.inputName.setText(profile.name)
            binding.inputAge.setText(profile.age.toString())
            binding.inputHeight.setText(profile.heightCm.toString())
            binding.inputWeight.setText(profile.weightKg.toString())
            binding.inputTargetWeight.setText(profile.targetWeightKg.toString())
            binding.spinnerActivity.setSelection(profile.activityLevel)
            binding.spinnerSex.setSelection(if (profile.sex == "female") 1 else 0)
        }

        binding.buttonSave.setOnClickListener { save() }
    }

    private fun save() {
        val name = binding.inputName.text.toString().trim()
        val age = binding.inputAge.text.toString().toIntOrNull()
        val height = binding.inputHeight.text.toString().toFloatOrNull()
        val weight = binding.inputWeight.text.toString().toFloatOrNull()
        val target = binding.inputTargetWeight.text.toString().toFloatOrNull()

        if (name.isBlank() || age == null || height == null || weight == null || target == null) {
            Toast.makeText(this, "Please fill in every field", Toast.LENGTH_SHORT).show()
            return
        }
        if (age !in 10..120 || height !in 80f..250f || weight !in 25f..400f || target !in 25f..400f) {
            Toast.makeText(this, "Please check the values — some look out of range", Toast.LENGTH_LONG).show()
            return
        }

        profile.name = name
        profile.age = age
        profile.heightCm = height
        profile.weightKg = weight
        profile.targetWeightKg = target
        profile.activityLevel = binding.spinnerActivity.selectedItemPosition
        profile.sex = if (binding.spinnerSex.selectedItemPosition == 1) "female" else "male"

        Toast.makeText(
            this,
            "Saved! Daily target: ${profile.dailyCalorieTarget()} kcal",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }
}

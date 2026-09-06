package com.caloriecam.app

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.caloriecam.app.databinding.ActivityResultBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shows the captured photo, runs the AI analysis, lets the user adjust the
 * numbers, then saves the meal locally and pushes a row to the Google Sheet.
 */
class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var profile: UserProfile
    private lateinit var db: MealDb
    private var photoFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profile = UserProfile(this)
        db = MealDb(this)

        val path = intent.getStringExtra(EXTRA_PHOTO_PATH)
        val file = path?.let { File(it) }
        if (file == null || !file.exists()) {
            Toast.makeText(this, "Photo not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        photoFile = file
        binding.imagePhoto.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))

        binding.buttonSave.setOnClickListener { saveMeal() }
        binding.buttonRetry.setOnClickListener { analyze(file) }

        analyze(file)
    }

    private fun analyze(file: File) {
        setLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = ClaudeApi.analyzePhoto(file, profile.apiKey)
                withContext(Dispatchers.Main) {
                    binding.inputFood.setText(result.foodName)
                    binding.inputCalories.setText(result.calories.toString())
                    binding.inputProtein.setText(result.proteinG.toString())
                    binding.inputCarbs.setText(result.carbsG.toString())
                    binding.inputFat.setText(result.fatG.toString())
                    binding.textNotes.text = result.notes
                    setLoading(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    binding.textNotes.text =
                        "Analysis failed: ${e.message ?: "unknown error"}. " +
                        "You can enter the values manually or retry."
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressAnalyzing.visibility = if (loading) View.VISIBLE else View.GONE
        binding.textAnalyzing.visibility = if (loading) View.VISIBLE else View.GONE
        binding.buttonSave.isEnabled = !loading
        binding.buttonRetry.isEnabled = !loading
    }

    private fun saveMeal() {
        val food = binding.inputFood.text.toString().trim()
        val calories = binding.inputCalories.text.toString().toIntOrNull()
        if (food.isBlank() || calories == null) {
            Toast.makeText(this, "Food name and calories are required", Toast.LENGTH_SHORT).show()
            return
        }

        val entry = MealEntry(
            date = MealDb.todayDate(),
            time = MealDb.nowTime(),
            foodName = food,
            calories = calories,
            proteinG = binding.inputProtein.text.toString().toIntOrNull() ?: 0,
            carbsG = binding.inputCarbs.text.toString().toIntOrNull() ?: 0,
            fatG = binding.inputFat.text.toString().toIntOrNull() ?: 0,
            notes = binding.textNotes.text.toString(),
            synced = false
        )

        binding.buttonSave.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val id = db.insert(entry)
            val pushed = SheetSync.pushRow(profile.sheetUrl, profile, entry)
            if (pushed) db.markSynced(id)
            photoFile?.delete()
            withContext(Dispatchers.Main) {
                val msg = when {
                    pushed -> "Saved and synced to your Google Sheet ✔"
                    profile.sheetUrl.isBlank() -> "Saved locally. Add your Sheet URL in Settings to sync."
                    else -> "Saved locally. Sheet sync failed — will retry later."
                }
                Toast.makeText(this@ResultActivity, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_PHOTO_PATH = "photo_path"
    }
}

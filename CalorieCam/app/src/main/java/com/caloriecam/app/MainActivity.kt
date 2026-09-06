package com.caloriecam.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.caloriecam.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var profile: UserProfile
    private lateinit var db: MealDb
    private var pendingPhoto: File? = null

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val photo = pendingPhoto
            if (ok && photo != null && photo.exists()) {
                startActivity(
                    Intent(this, ResultActivity::class.java)
                        .putExtra(ResultActivity.EXTRA_PHOTO_PATH, photo.absolutePath)
                )
            }
        }

    private val requestCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera()
            else Toast.makeText(this, "Camera permission is needed to snap meals", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        profile = UserProfile(this)
        db = MealDb(this)

        if (!profile.isComplete) {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.fabCapture.setOnClickListener { captureMeal() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        // Opportunistically retry rows that failed to reach the Google Sheet.
        lifecycleScope.launch(Dispatchers.IO) {
            val n = SheetSync.syncPending(db, profile)
            if (n > 0) withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Synced $n pending meal(s) to your sheet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refresh() {
        if (!profile.isComplete) return

        val meals = db.mealsForDate(MealDb.todayDate())
        val consumed = meals.sumOf { it.calories }
        val target = profile.dailyCalorieTarget()

        binding.textGreeting.text = "Hi ${profile.name}!"
        binding.textCalories.text = "$consumed / $target kcal"
        binding.progressCalories.max = target
        binding.progressCalories.progress = consumed.coerceAtMost(target)

        val protein = meals.sumOf { it.proteinG }
        val carbs = meals.sumOf { it.carbsG }
        val fat = meals.sumOf { it.fatG }
        binding.textMacros.text = "Protein ${protein}g · Carbs ${carbs}g · Fat ${fat}g"

        val s = SuggestionEngine.forToday(profile, consumed)
        binding.textGoalHeadline.text = s.headline
        binding.textStatus.text = s.statusLine
        binding.textDietTip.text = "🍽 ${s.dietTip}"
        binding.textExerciseTip.text = "🏃 ${s.exerciseTip}"

        binding.textMealsToday.text =
            if (meals.isEmpty()) "No meals logged yet today. Tap the camera to start!"
            else meals.joinToString("\n") { "• ${it.time}  ${it.foodName} — ${it.calories} kcal" }
    }

    private fun captureMeal() {
        if (profile.apiKey.isBlank()) {
            Toast.makeText(this, "Add your Claude API key in Settings first", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestCamera.launch(Manifest.permission.CAMERA)
        } else {
            launchCamera()
        }
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "photos").apply { mkdirs() }
        val photo = File(dir, "meal_${System.currentTimeMillis()}.jpg")
        pendingPhoto = photo
        val uri: Uri = FileProvider.getUriForFile(this, "com.caloriecam.app.fileprovider", photo)
        takePicture.launch(uri)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_HISTORY, 0, getString(R.string.history))
        menu.add(0, MENU_PROFILE, 1, getString(R.string.profile))
        menu.add(0, MENU_SETTINGS, 2, getString(R.string.settings))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_HISTORY -> startActivity(Intent(this, HistoryActivity::class.java))
            MENU_PROFILE -> startActivity(Intent(this, ProfileActivity::class.java))
            MENU_SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        private const val MENU_HISTORY = 1
        private const val MENU_PROFILE = 2
        private const val MENU_SETTINGS = 3
    }
}

package com.ideacapture.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.ChipGroup
import java.util.Locale

class AddEditIdeaActivity : AppCompatActivity() {

    private lateinit var store: IdeaStore
    private lateinit var ideaInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var statusGroup: ChipGroup

    private var existingIdea: Idea? = null
    // Which field the next speech result should go into
    private var speechTarget: EditText? = null

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            val target = speechTarget
            if (!spoken.isNullOrBlank() && target != null) {
                val existing = target.text.toString().trim()
                val combined = if (existing.isEmpty()) spoken else "$existing $spoken"
                target.setText(combined)
                target.setSelection(combined.length)
            }
        }
        speechTarget = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit)

        store = IdeaStore(this)
        ideaInput = findViewById(R.id.ideaInput)
        descriptionInput = findViewById(R.id.descriptionInput)
        statusGroup = findViewById(R.id.statusGroup)

        findViewById<ImageButton>(R.id.micIdeaButton).setOnClickListener {
            startSpeechInput(ideaInput, getString(R.string.speech_prompt_idea))
        }
        findViewById<ImageButton>(R.id.micDescriptionButton).setOnClickListener {
            startSpeechInput(descriptionInput, getString(R.string.speech_prompt_description))
        }
        findViewById<Button>(R.id.saveButton).setOnClickListener { save() }
        findViewById<Button>(R.id.cancelButton).setOnClickListener { finish() }

        val ideaId = intent.getStringExtra(EXTRA_IDEA_ID)
        if (ideaId != null) {
            existingIdea = store.get(ideaId)
        }
        existingIdea?.let { idea ->
            setTitle(R.string.edit_idea_title)
            ideaInput.setText(idea.title)
            descriptionInput.setText(idea.description)
            checkStatusChip(idea.status)
        } ?: run {
            setTitle(R.string.new_idea_title)
            checkStatusChip(IdeaStatus.NOT_SO_URGENT)
        }
    }

    private fun checkStatusChip(status: IdeaStatus) {
        statusGroup.check(
            when (status) {
                IdeaStatus.URGENT -> R.id.statusUrgent
                IdeaStatus.NOT_SO_URGENT -> R.id.statusNotSoUrgent
                IdeaStatus.WIP -> R.id.statusWip
            }
        )
    }

    private fun selectedStatus(): IdeaStatus = when (statusGroup.checkedChipId) {
        R.id.statusUrgent -> IdeaStatus.URGENT
        R.id.statusWip -> IdeaStatus.WIP
        else -> IdeaStatus.NOT_SO_URGENT
    }

    private fun startSpeechInput(target: EditText, prompt: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // Recognize English speech and turn it into text
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        try {
            speechTarget = target
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            speechTarget = null
            Toast.makeText(this, R.string.speech_not_available, Toast.LENGTH_LONG).show()
        }
    }

    private fun save() {
        val title = ideaInput.text.toString().trim()
        if (title.isEmpty()) {
            ideaInput.error = getString(R.string.error_idea_required)
            ideaInput.requestFocus()
            return
        }
        val description = descriptionInput.text.toString().trim()
        val status = selectedStatus()

        val idea = existingIdea?.apply {
            this.title = title
            this.description = description
            this.status = status
            this.updatedAt = System.currentTimeMillis()
        } ?: Idea(title = title, description = description, status = status)

        store.upsert(idea)
        finish()
    }

    companion object {
        const val EXTRA_IDEA_ID = "extra_idea_id"
    }
}

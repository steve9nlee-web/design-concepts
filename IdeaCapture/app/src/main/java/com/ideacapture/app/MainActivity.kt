package com.ideacapture.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var store: IdeaStore
    private lateinit var adapter: IdeasAdapter
    private lateinit var emptyView: TextView
    private lateinit var filterGroup: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        store = IdeaStore(this)
        emptyView = findViewById(R.id.emptyView)
        filterGroup = findViewById(R.id.filterGroup)

        adapter = IdeasAdapter(
            onClick = { idea ->
                startActivity(
                    Intent(this, AddEditIdeaActivity::class.java)
                        .putExtra(AddEditIdeaActivity.EXTRA_IDEA_ID, idea.id)
                )
            },
            onLongClick = { idea -> confirmDelete(idea) },
            onStatusClick = { idea -> cycleStatus(idea) }
        )

        val list = findViewById<RecyclerView>(R.id.ideasList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<FloatingActionButton>(R.id.addFab).setOnClickListener {
            startActivity(Intent(this, AddEditIdeaActivity::class.java))
        }

        filterGroup.setOnCheckedStateChangeListener { _, _ -> refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun currentFilter(): IdeaStatus? = when (filterGroup.checkedChipId) {
        R.id.chipUrgent -> IdeaStatus.URGENT
        R.id.chipNotSoUrgent -> IdeaStatus.NOT_SO_URGENT
        R.id.chipWip -> IdeaStatus.WIP
        else -> null
    }

    private fun refresh() {
        val filter = currentFilter()
        val ideas = store.loadAll()
            .filter { filter == null || it.status == filter }
            // Urgent first, then WIP, then not so urgent; newest first within a group
            .sortedWith(
                compareBy<Idea> {
                    when (it.status) {
                        IdeaStatus.URGENT -> 0
                        IdeaStatus.WIP -> 1
                        IdeaStatus.NOT_SO_URGENT -> 2
                    }
                }.thenByDescending { it.updatedAt }
            )
        adapter.submit(ideas)
        emptyView.visibility = if (ideas.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun cycleStatus(idea: Idea) {
        idea.status = when (idea.status) {
            IdeaStatus.URGENT -> IdeaStatus.NOT_SO_URGENT
            IdeaStatus.NOT_SO_URGENT -> IdeaStatus.WIP
            IdeaStatus.WIP -> IdeaStatus.URGENT
        }
        idea.updatedAt = System.currentTimeMillis()
        store.upsert(idea)
        refresh()
    }

    private fun confirmDelete(idea: Idea) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, idea.title))
            .setPositiveButton(R.string.delete) { _, _ ->
                store.delete(idea.id)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

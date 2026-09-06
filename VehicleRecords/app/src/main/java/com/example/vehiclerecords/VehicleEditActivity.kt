package com.example.vehiclerecords

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import java.util.Calendar

class VehicleEditActivity : AppCompatActivity() {

    private var existing: Vehicle? = null

    private lateinit var plateInput: EditText
    private lateinit var makeInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var colorInput: EditText
    private lateinit var typeGroup: MaterialButtonToggleGroup

    private lateinit var roadTaxDate: EditText
    private lateinit var roadTaxCost: EditText
    private lateinit var insuranceDate: EditText
    private lateinit var insuranceCost: EditText
    private lateinit var inspectionDate: EditText
    private lateinit var inspectionCost: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        plateInput = findViewById(R.id.plateInput)
        makeInput = findViewById(R.id.makeInput)
        modelInput = findViewById(R.id.modelInput)
        colorInput = findViewById(R.id.colorInput)
        typeGroup = findViewById(R.id.typeGroup)

        roadTaxDate = findViewById(R.id.roadTaxDateInput)
        roadTaxCost = findViewById(R.id.roadTaxCostInput)
        insuranceDate = findViewById(R.id.insuranceDateInput)
        insuranceCost = findViewById(R.id.insuranceCostInput)
        inspectionDate = findViewById(R.id.inspectionDateInput)
        inspectionCost = findViewById(R.id.inspectionCostInput)

        listOf(roadTaxDate, insuranceDate, inspectionDate).forEach { field ->
            field.setOnClickListener { showDatePicker(field) }
        }

        val deleteButton = findViewById<Button>(R.id.deleteButton)
        findViewById<Button>(R.id.saveButton).setOnClickListener { save() }

        val vehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID)
        existing = vehicleId?.let { VehicleStore.find(this, it) }

        val vehicle = existing
        if (vehicle != null) {
            title = getString(R.string.edit_vehicle)
            plateInput.setText(vehicle.plateNo)
            makeInput.setText(vehicle.make)
            modelInput.setText(vehicle.model)
            colorInput.setText(vehicle.color)
            typeGroup.check(
                when (vehicle.type) {
                    Vehicle.TYPE_MOTORCYCLE -> R.id.typeMotorcycle
                    Vehicle.TYPE_VAN -> R.id.typeVan
                    else -> R.id.typeCar
                }
            )
            roadTaxDate.setText(vehicle.roadTax.date)
            roadTaxCost.setText(vehicle.roadTax.cost)
            insuranceDate.setText(vehicle.insurance.date)
            insuranceCost.setText(vehicle.insurance.cost)
            inspectionDate.setText(vehicle.inspection.date)
            inspectionCost.setText(vehicle.inspection.cost)

            deleteButton.visibility = View.VISIBLE
            deleteButton.setOnClickListener { confirmDelete(vehicle) }
        } else {
            title = getString(R.string.add_vehicle)
            typeGroup.check(R.id.typeCar)
            deleteButton.visibility = View.GONE
        }
    }

    private fun showDatePicker(field: EditText) {
        val calendar = Calendar.getInstance()
        val current = field.text.toString()
        if (current.isNotBlank()) {
            try {
                Renewal.DATE_FORMAT.parse(current)?.let { calendar.time = it }
            } catch (e: Exception) {
                // Ignore unparseable text; picker opens on today.
            }
        }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                field.setText(String.format("%04d-%02d-%02d", year, month + 1, day))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun selectedType(): String = when (typeGroup.checkedButtonId) {
        R.id.typeMotorcycle -> Vehicle.TYPE_MOTORCYCLE
        R.id.typeVan -> Vehicle.TYPE_VAN
        else -> Vehicle.TYPE_CAR
    }

    private fun save() {
        val plate = plateInput.text.toString().trim().uppercase()
        if (plate.isBlank()) {
            plateInput.error = getString(R.string.plate_required)
            plateInput.requestFocus()
            return
        }

        val vehicle = Vehicle(
            id = existing?.id ?: Vehicle().id,
            plateNo = plate,
            make = makeInput.text.toString().trim(),
            model = modelInput.text.toString().trim(),
            color = colorInput.text.toString().trim(),
            type = selectedType(),
            roadTax = Renewal(roadTaxDate.text.toString().trim(), roadTaxCost.text.toString().trim()),
            insurance = Renewal(insuranceDate.text.toString().trim(), insuranceCost.text.toString().trim()),
            inspection = Renewal(inspectionDate.text.toString().trim(), inspectionCost.text.toString().trim())
        )
        VehicleStore.upsert(this, vehicle)
        finish()
    }

    private fun confirmDelete(vehicle: Vehicle) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_title, vehicle.plateNo))
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                VehicleStore.delete(this, vehicle.id)
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_VEHICLE_ID = "vehicle_id"
    }
}

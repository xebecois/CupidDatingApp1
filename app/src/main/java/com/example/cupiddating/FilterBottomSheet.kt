package com.example.cupiddating

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.RangeSlider

class FilterBottomSheet : BottomSheetDialogFragment() {

    // 1. Define an Interface to pass data back
    interface FilterListener {
        fun onFilterApplied(gender: String, minAge: Int, maxAge: Int)
    }

    private var listener: FilterListener? = null

    // 2. Attach the listener when the fragment attaches to MainActivity
    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as FilterListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement FilterListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_layout_filter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnContinue = view.findViewById<Button>(R.id.btnContinue)
        val ageSlider = view.findViewById<RangeSlider>(R.id.ageSlider)
        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupGender)
        val tvMinAge = view.findViewById<TextView>(R.id.agefilter)
        val tvMaxAge = view.findViewById<TextView>(R.id.textView2)
        val tvClear = view.findViewById<TextView>(R.id.tvClear)

        // Update text numbers when slider moves
        ageSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            tvMinAge.text = values[0].toInt().toString()
            tvMaxAge.text = values[1].toInt().toString()
        }

        btnContinue.setOnClickListener {
            // A. Get Gender
            val selectedGender = when (toggleGroup.checkedButtonId) {
                R.id.btnBoys -> "Male"
                R.id.btnGirls -> "Female"
                else -> "Both" // Default or if "Both" is selected
            }

            // B. Get Age Range
            val values = ageSlider.values
            val minAge = values[0].toInt()
            val maxAge = values[1].toInt()

            // C. Send data to MainActivity
            listener?.onFilterApplied(selectedGender, minAge, maxAge)
            dismiss()
        }

        tvClear.setOnClickListener {
            // Reset to default: Both genders, 18-80 age
            listener?.onFilterApplied("Both", 18, 80)
            dismiss()
        }
    }

    // Transparent background for rounded corners
    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
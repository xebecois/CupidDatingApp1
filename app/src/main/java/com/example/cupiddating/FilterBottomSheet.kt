package com.example.cupiddating

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.RangeSlider

class FilterBottomSheet : BottomSheetDialogFragment() {

    // This connects your "filter xml" to this class
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Ensure this layout name matches your filter XML filename exactly
        return inflater.inflate(R.layout.activity_layout_filter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find the views inside the popup
        val btnContinue = view.findViewById<Button>(R.id.btnContinue)
        val ageSlider = view.findViewById<RangeSlider>(R.id.ageSlider)

        btnContinue.setOnClickListener {
            // Get the current values from the slider
            val selectedAges = ageSlider.values // Returns a List<Float> [e.g., 20.0, 28.0]

            // Logic for when the user clicks continue (like reloading the list)
            // For now, it just closes the popup
            dismiss()
        }

        val tvClear = view.findViewById<View>(R.id.tvClear)
        tvClear.setOnClickListener {
            // Logic to reset filters
            dismiss()
        }
    }

    // Optional: This makes the background transparent so your rounded corners look nice
    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
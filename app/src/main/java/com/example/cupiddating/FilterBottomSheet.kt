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

    interface FilterListener {
        fun onFilterApplied(gender: String, minAge: Int, maxAge: Int)
    }

    private var listener: FilterListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? FilterListener ?: throw ClassCastException("$context must implement FilterListener")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_layout_filter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ageSlider = view.findViewById<RangeSlider>(R.id.ageSlider)
        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupGender)
        val tvMinAge = view.findViewById<TextView>(R.id.agefilter)
        val tvMaxAge = view.findViewById<TextView>(R.id.textView2)

        ageSlider.addOnChangeListener { slider, _, _ ->
            tvMinAge.text = slider.values[0].toInt().toString()
            tvMaxAge.text = slider.values[1].toInt().toString()
        }

        view.findViewById<Button>(R.id.btnContinue).setOnClickListener {
            val gender = when (toggleGroup.checkedButtonId) {
                R.id.btnBoys -> "Male"
                R.id.btnGirls -> "Female"
                else -> "Both"
            }
            val minAge = ageSlider.values[0].toInt()
            val maxAge = ageSlider.values[1].toInt()

            listener?.onFilterApplied(gender, minAge, maxAge)
            dismiss()
        }

        view.findViewById<TextView>(R.id.tvClear).setOnClickListener {
            listener?.onFilterApplied("Both", 18, 80)
            dismiss()
        }
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme
}
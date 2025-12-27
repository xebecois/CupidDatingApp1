package com.example.cupiddating

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class MainProfileDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_profile_details)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 1. Get Data from Intent
        val name = intent.getStringExtra("name") ?: "Unknown"
        val age = intent.getIntExtra("age", 18)
        val location = intent.getStringExtra("location") ?: "Unknown"
        val bio = intent.getStringExtra("bio") ?: "No bio available."
        val distance = intent.getIntExtra("distance", 0)
        val passions = intent.getStringArrayListExtra("passions") ?: arrayListOf()
        val images = intent.getStringArrayListExtra("images") ?: arrayListOf()
        val myPassions = intent.getStringArrayListExtra("myPassions") ?: arrayListOf()

        // 2. Bind Basic Views
        findViewById<TextView>(R.id.tv_detail_name_age).text = "$name, $age"
        findViewById<TextView>(R.id.tv_detail_location).text = location
        findViewById<TextView>(R.id.tv_detail_bio).text = bio
        findViewById<TextView>(R.id.tv_distance).text = "$distance km"

        // 3. Setup Parallax Image (Header) - Use the first image
        val headerImage = findViewById<ImageView>(R.id.iv_detail_profile_image)
        if (images.isNotEmpty()) {
            Glide.with(this)
                .load(images[0])
                .centerCrop()
                .into(headerImage)
        }

        // 4. Setup Lists
        setupInterests(passions, myPassions)
        val galleryOnly = if (images.isNotEmpty()) images.drop(1) else emptyList()
        setupGallery(galleryOnly)

        // 5. Setup Action Buttons
        setupActionButtons()

        // 6. Setup Back Button
        findViewById<ImageButton>(R.id.btnUndo_more).setOnClickListener {
            finish() // Just close, no action
        }

        // Optional: Expand/Collapse logic for "See All"
        findViewById<Button>(R.id.btn_seeAll).setOnClickListener {
            // Logic to show all photos or expand
        }
    }

    private fun setupInterests(theirPassions: List<String>, myPassions: List<String>) {
        val chipGroup = findViewById<ChipGroup>(R.id.cg_detail_interests_container)
        chipGroup.removeAllViews()

        // Define Colors
        val brandColor = Color.parseColor("#E94057")
        val whiteColor = Color.WHITE

        for (interest in theirPassions) {
            val chip = Chip(this)
            chip.text = interest
            chip.textSize = 14f
            chip.isCheckable = false
            chip.isClickable = false

            // Check if this passion matches one of MINE
            val isMatch = myPassions.any { it.equals(interest, ignoreCase = true) }

            if (isMatch) {
                // --- MATCH: Filled Color (Pink Background, White Text) ---
                chip.chipBackgroundColor = ColorStateList.valueOf(brandColor)
                chip.setTextColor(whiteColor)
                chip.chipStrokeWidth = 0f
            } else {
                // --- NO MATCH: Outline Style (Transparent BG, Pink Border, Pink Text) ---
                chip.chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                chip.chipStrokeColor = ColorStateList.valueOf(brandColor)
                chip.chipStrokeWidth = 3f // visible border
                chip.setTextColor(brandColor)
            }

            chipGroup.addView(chip)
        }
    }

    private fun setupGallery(photos: List<String>) {
        val rvGallery = findViewById<RecyclerView>(R.id.rv_gallery_list)

        // Single column grid as requested
        rvGallery.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        // Pass all photos to the adapter
        val adapter = GalleryAdapter(photos)
        rvGallery.adapter = adapter
    }

    private fun setupActionButtons() {
        val btnPass = findViewById<ImageButton>(R.id.btn_detailPass)
        val btnLike = findViewById<ImageButton>(R.id.btn_detailLike)
        val btnSuperLike = findViewById<ImageButton>(R.id.btn_detailSuperLike)

        btnPass.setOnClickListener { finishWithAction("PASS") }
        btnLike.setOnClickListener { finishWithAction("LIKE") }
        btnSuperLike.setOnClickListener { finishWithAction("SUPERLIKE") }
    }

    private fun finishWithAction(action: String) {
        val resultIntent = Intent()
        resultIntent.putExtra("ACTION", action)
        setResult(Activity.RESULT_OK, resultIntent)
        finish() // Closes this activity and returns to MainActivity
    }

    // --- Internal Gallery Adapter ---
    class GalleryAdapter(private val photoUrls: List<String>) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

        inner class GalleryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.iv_gallery_item)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
            // Inflate your specific item_gallery_image.xml
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_image, parent, false)
            return GalleryViewHolder(view)
        }

        override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
            val url = photoUrls[position]

            Glide.with(holder.itemView.context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop() // Ensures images fit uniformly in the card
                .placeholder(android.R.color.darker_gray)
                .into(holder.imageView)
        }

        override fun getItemCount(): Int = photoUrls.size
    }
}
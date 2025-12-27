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

        setupWindowInsets()
        bindProfileData()
        setupActionButtons()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun bindProfileData() {
        val name = intent.getStringExtra("name") ?: "Unknown"
        val age = intent.getIntExtra("age", 18)
        val location = intent.getStringExtra("location") ?: "Unknown"
        val bio = intent.getStringExtra("bio") ?: "No bio available."
        val distance = intent.getIntExtra("distance", 0)
        val passions = intent.getStringArrayListExtra("passions") ?: arrayListOf()
        val images = intent.getStringArrayListExtra("images") ?: arrayListOf()
        val myPassions = intent.getStringArrayListExtra("myPassions") ?: arrayListOf()

        findViewById<TextView>(R.id.tv_detail_name_age).text = "$name, $age"
        findViewById<TextView>(R.id.tv_detail_location).text = location
        findViewById<TextView>(R.id.tv_detail_bio).text = bio
        findViewById<TextView>(R.id.tv_distance).text = "$distance km"

        val headerImage = findViewById<ImageView>(R.id.iv_detail_profile_image)
        if (images.isNotEmpty()) {
            Glide.with(this).load(images[0]).centerCrop().into(headerImage)
        }

        setupInterests(passions, myPassions)
        setupGallery(if (images.size > 1) images.drop(1) else emptyList())
    }

    private fun setupInterests(theirPassions: List<String>, myPassions: List<String>) {
        val chipGroup = findViewById<ChipGroup>(R.id.cg_detail_interests_container)
        chipGroup.removeAllViews()

        val brandColor = Color.parseColor("#E94057")
        val whiteColor = Color.WHITE

        for (interest in theirPassions) {
            val isMatch = myPassions.any { it.equals(interest, ignoreCase = true) }
            val chip = Chip(this).apply {
                text = interest
                textSize = 14f
                isCheckable = false
                isClickable = false

                if (isMatch) {
                    chipBackgroundColor = ColorStateList.valueOf(brandColor)
                    setTextColor(whiteColor)
                    chipStrokeWidth = 0f
                } else {
                    chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                    chipStrokeColor = ColorStateList.valueOf(brandColor)
                    chipStrokeWidth = 3f
                    setTextColor(brandColor)
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun setupGallery(photos: List<String>) {
        findViewById<RecyclerView>(R.id.rv_gallery_list).apply {
            layoutManager = LinearLayoutManager(this@MainProfileDetailsActivity)
            adapter = GalleryAdapter(photos)
        }
    }

    private fun setupActionButtons() {
        findViewById<ImageButton>(R.id.btn_detailPass).setOnClickListener { finishWithAction("PASS") }
        findViewById<ImageButton>(R.id.btn_detailLike).setOnClickListener { finishWithAction("LIKE") }
        findViewById<ImageButton>(R.id.btn_detailSuperLike).setOnClickListener { finishWithAction("SUPERLIKE") }
        findViewById<ImageButton>(R.id.btnUndo_more).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_seeAll).setOnClickListener { /* Logic for See All */ }
    }

    private fun finishWithAction(action: String) {
        val resultIntent = Intent().apply { putExtra("ACTION", action) }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    class GalleryAdapter(private val photoUrls: List<String>) :
        RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

        class GalleryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.iv_gallery_item)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_image, parent, false)
            return GalleryViewHolder(view)
        }

        override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
            Glide.with(holder.itemView.context)
                .load(photoUrls[position])
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(android.R.color.darker_gray)
                .into(holder.imageView)
        }

        override fun getItemCount() = photoUrls.size
    }
}
package com.example.cupiddating

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Intent
import android.widget.ImageButton

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 1. Handle Window Insets (Edge-to-Edge)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 2. Find the container where we will stack the cards
        // Note: Ensure your LinearLayout in activity_main.xml has id: ll_mainContainer
        val mainContainer = findViewById<LinearLayout>(R.id.LL_mainPageInflater)

        // 3. Initialize Firebase
        val db = FirebaseFirestore.getInstance()


        val btnMatches : ImageButton = findViewById(R.id.btnMatches)
        btnMatches.setOnClickListener {
            val intent = Intent(this, MatchesPageActivity::class.java)
            startActivity(intent)
        }

        // 4. Fetch Data from 'tbl_users'
        db.collection("tbl_users").get()
            .addOnSuccessListener { documents ->

                // Loop through every user found in the database
                for (document in documents) {

                    // --- A. INFLATE THE TEMPLATE ---
                    val inflater = LayoutInflater.from(this)
                    // We use 'false' because we will add it manually with addView() later
                    val cardView = inflater.inflate(R.layout.activity_item_user_card, mainContainer, false)

                    // --- B. FIND VIEWS INSIDE THE CARD ---
                    val tvNameAge = cardView.findViewById<TextView>(R.id.tv_userNameAge)
                    val tvJob = cardView.findViewById<TextView>(R.id.tv_userJob)
                    val tvMatch = cardView.findViewById<TextView>(R.id.tv_matchPercent)
                    val viewPager = cardView.findViewById<ViewPager2>(R.id.vp_imageSlider)

                    // --- C. GET DATA FROM FIRESTORE ---
                    // Make sure these field names match your Firebase exactly!
                    val name = document.getString("name") ?: "Unknown"
                    val age = document.getLong("age") ?: 18
                    val job = document.getString("job") ?: "No Job Listed"
                    val matchScore = document.getLong("match_percent") ?: 50

                    // Get the array of image URLs
                    // In Firebase, this should be an Array/List of Strings
                    val imagesList = document.get("images") as? List<String> ?: emptyList()

                    // --- D. POPULATE THE VIEW ---
                    tvNameAge.text = "$name, $age"
                    tvJob.text = job
                    tvMatch.text = "$matchScore% Match"

                    // --- E. SETUP IMAGE SLIDER (ViewPager2) ---
                    // We connect the adapter to handle the swiping images
                    if (imagesList.isNotEmpty()) {
                        val adapter = ImageSliderAdapter(imagesList)
                        viewPager.adapter = adapter
                    }

                    // --- FIX: PREVENT PAGE SCROLLING WHEN SWIPING IMAGES ---
                    // This tells the main ScrollView to stop moving when you touch the slider
                    viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageScrollStateChanged(state: Int) {
                            super.onPageScrollStateChanged(state)
                            // If the user is dragging the image, disable the main vertical scroll
                            val shouldDisableScroll = (state == ViewPager2.SCROLL_STATE_DRAGGING)
                            viewPager.parent.requestDisallowInterceptTouchEvent(shouldDisableScroll)
                        }
                    })

                    // --- F. ADD TO MAIN LAYOUT ---
                    mainContainer.addView(cardView)
                }
            }
            .addOnFailureListener { exception ->
                // Handle error (optional: Log it)
                exception.printStackTrace()
            }
    }

    // --- INNER CLASS: ADAPTER FOR IMAGE SLIDER ---
    // This class handles the logic for swiping images inside the card
    inner class ImageSliderAdapter(private val imageUrls: List<String>) :
        RecyclerView.Adapter<ImageSliderAdapter.ImageViewHolder>() {

//        inner class ImageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//            val imageView: ImageView = itemView.findViewById(R.id.iv_sliderImage)
//        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            // We need a simple layout for the image item itself.
            // Since we don't have one, we create an ImageView programmatically here.
            val imageView = ImageView(parent.context)
            imageView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            return ImageViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            // Use Glide to load the URL into the ImageView
            Glide.with(holder.itemView.context)
                .load(imageUrls[position])
                .into(holder.imageView)
        }

        override fun getItemCount(): Int = imageUrls.size

        // ViewHolder class
        inner class ImageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)
    }
}
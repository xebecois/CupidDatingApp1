package com.example.cupiddating

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy // Required for caching

// 1. Data Model
data class DatingUser(
    val name: String,
    val age: Int,
    val location: String,
    val matchPercent: Int,
    val images: List<String>
)

// 2. Adapter
class CardStackAdapter(
    private var users: List<DatingUser> = emptyList()
) : RecyclerView.Adapter<CardStackAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNameAge: TextView = view.findViewById(R.id.tv_userNameAge)
        val tvLocation: TextView = view.findViewById(R.id.tv_userLocation)
        val tvMatch: TextView = view.findViewById(R.id.tv_matchPercent)
        val viewPager: ViewPager2 = view.findViewById(R.id.vp_imageSlider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.activity_item_user_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]

        holder.tvNameAge.text = "${user.name}, ${user.age}"
        holder.tvLocation.text = user.location
        holder.tvMatch.text = "${user.matchPercent}% Match"

        if (user.images.isNotEmpty()) {
            holder.viewPager.adapter = InnerImageAdapter(user.images)
        }
    }

    override fun getItemCount(): Int = users.size

    fun setUsers(newUsers: List<DatingUser>) {
        this.users = newUsers
        notifyDataSetChanged()
    }

    // --- Inner Adapter for the Images ---
    inner class InnerImageAdapter(private val imageUrls: List<String>) :
        RecyclerView.Adapter<InnerImageAdapter.ImageViewHolder>() {

        inner class ImageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val imageView = ImageView(parent.context)
            imageView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            return ImageViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            // --- LAZY LOADING & OPTIMIZATION APPLIED HERE ---
            Glide.with(holder.itemView.context)
                .load(imageUrls[position])
                .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache image to disk (Lazy Load optimization)
                .thumbnail(0.1f) // Load a low-quality version immediately
                .placeholder(android.R.color.darker_gray) // Show gray while loading
                .into(holder.imageView)
        }

        override fun getItemCount(): Int = imageUrls.size
    }
}
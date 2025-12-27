// CardStackAdapter.kt

package com.example.cupiddating

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

// 1. Data Model
data class DatingUser(
    val userId: String,
    val name: String,
    val age: Int,
    val location: String,
    val matchPercent: Int,
    val images: List<String>,
    val passions: List<String> = emptyList(),
    val bio: String = "",
    val distance: Int = 0
)

// 2. Adapter
class CardStackAdapter(
    private var users: List<DatingUser> = emptyList()
) : RecyclerView.Adapter<CardStackAdapter.ViewHolder>() {

    // Listener for card clicks
    var onCardClick: ((DatingUser) -> Unit)? = null

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

        // --- FIX: Pass the 'user' object to the Inner Adapter ---
        val imageAdapter = InnerImageAdapter(user.images, user)
        holder.viewPager.adapter = imageAdapter

        // Keep this for clicks on the text/overlay areas
        holder.itemView.setOnClickListener {
            onCardClick?.invoke(user)
        }
    }

    override fun getItemCount(): Int = users.size

    fun setUsers(newUsers: List<DatingUser>) {
        this.users = newUsers
        notifyDataSetChanged()
    }

    fun getUser(position: Int): DatingUser? {
        return if (position in users.indices) users[position] else null
    }

    // --- Inner Adapter for the Images (Slider) ---
    // Update: Now accepts 'user: DatingUser' so we can handle clicks
    inner class InnerImageAdapter(
        private val imageUrls: List<String>,
        private val user: DatingUser
    ) : RecyclerView.Adapter<InnerImageAdapter.ImageViewHolder>() {

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
            Glide.with(holder.itemView.context)
                .load(imageUrls[position])
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .thumbnail(0.1f)
                .placeholder(android.R.color.darker_gray)
                .into(holder.imageView)

            // --- CRITICAL FIX ---
            // Trigger the main listener when the IMAGE itself is clicked
            holder.itemView.setOnClickListener {
                this@CardStackAdapter.onCardClick?.invoke(user)
            }
        }

        override fun getItemCount(): Int = imageUrls.size
    }
}
package com.example.cupiddating

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.widget.NestedScrollView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.*
import java.text.SimpleDateFormat
import java.util.*

class LayoutChatDialog(val otherUserId: String, val otherUserName: String, val myId: String) : BottomSheetDialogFragment() {

    private lateinit var container: LinearLayout
    private lateinit var chatInput: EditText
    private lateinit var scrollView: NestedScrollView
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())

    override fun onStart() {
        super.onStart()
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme)
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            it.layoutParams.height = (resources.displayMetrics.heightPixels * 0.9).toInt()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            behavior.isDraggable = true
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.layout_chat_dialog, container, false)
        val headerTitle = view.findViewById<TextView>(R.id.tv_chat_header)
        val statusText = view.findViewById<TextView>(R.id.tv_user_status)
        val profileIv = view.findViewById<ImageView>(R.id.chat_profile_image)

        headerTitle.text = otherUserName

        FirebaseFirestore.getInstance().collection("tbl_users")
            .whereEqualTo("user_id", otherUserId)
            .limit(1)
            .addSnapshotListener { snapshots, _ ->
                val document = snapshots?.documents?.firstOrNull() ?: return@addSnapshotListener
                val imageUrl = document.getString("profile_picture") ?: ""
                val isOnline = document.getBoolean("isOnline") ?: false

                if (imageUrl.isNotEmpty()) {
                    Glide.with(this).load(imageUrl).placeholder(R.drawable.profile_red).circleCrop().into(profileIv)
                } else {
                    profileIv.setImageResource(R.drawable.profile_red)
                }

                statusText.text = if (isOnline) "Online" else "Offline"
                statusText.setTextColor(if (isOnline) Color.parseColor("#4CAF50") else Color.GRAY)
            }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chatInput = view.findViewById(R.id.et_chat_input)
        container = view.findViewById(R.id.LL_chat_messages_container)
        scrollView = view.findViewById(R.id.scroll_chat)

        view.findViewById<ImageButton>(R.id.btn_send_message).setOnClickListener {
            val text = chatInput.text.toString().trim()
            if (text.isNotEmpty()) sendMessage(text)
        }

        dialog?.setCanceledOnTouchOutside(false)
        listenForMessages()
    }

    private fun listenForMessages() {
        FirebaseFirestore.getInstance().collection("tbl_messages")
            .addSnapshotListener { snapshots, e ->
                if (e != null || !isAdded || view == null) return@addSnapshotListener
                markMessagesAsSeen()

                val filteredDocs = snapshots!!.documents.filter { doc ->
                    val sId = doc.getString("sender_id") ?: ""
                    val rId = doc.getString("receiver_id") ?: ""
                    (sId == myId && rId == otherUserId) || (sId == otherUserId && rId == myId)
                }.sortedBy { it.getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate() ?: Date() }

                val lastId = filteredDocs.lastOrNull()?.id
                container.removeAllViews()
                var lastDateLabel: String? = null

                for (doc in filteredDocs) {
                    val sId = doc.getString("sender_id") ?: ""
                    val msg = doc.getString("message") ?: ""
                    val isSeen = doc.getBoolean("seen") ?: false
                    val ts = doc.getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate() ?: Date()

                    val dateLabel = dateFormat.format(ts)
                    if (dateLabel != lastDateLabel) {
                        addDateHeader(dateLabel)
                        lastDateLabel = dateLabel
                    }
                    addBubble(msg, sId == myId, ts, isSeen, doc.id == lastId)
                }
                scrollView.post { if (isAdded) scrollView.smoothScrollTo(0, container.bottom) }
            }
    }

    private fun addDateHeader(dateText: String) {
        val tvDate = TextView(context).apply {
            text = dateText
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 32, 0, 16) }
        }
        container.addView(tvDate)
    }

    private fun addBubble(text: String, isMe: Boolean, timestamp: Date, isSeen: Boolean, isMostRecent: Boolean) {
        val messageGroup = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val tvMsg = TextView(context).apply {
            this.text = text
            textSize = 16f
            setPadding(40, 20, 40, 20)
            maxWidth = (resources.displayMetrics.widthPixels * 0.7).toInt()
            setBackgroundResource(if (isMe) R.drawable.bg_message_me else R.drawable.bg_message_them)
            setTextColor(if (isMe) Color.WHITE else Color.BLACK)
        }

        val groupParams = LinearLayout.LayoutParams(-2, -2).apply {
            setMargins(10, 8, 10, 8)
            gravity = if (isMe) Gravity.END else Gravity.START
        }

        messageGroup.layoutParams = groupParams
        messageGroup.addView(tvMsg)

        if (isMostRecent) {
            val statusLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isMe) Gravity.END else Gravity.START
            }
            val tvTime = TextView(context).apply {
                this.text = timeFormat.format(timestamp)
                textSize = 11f
                setTextColor(Color.parseColor("#AAAAAA"))
                setPadding(30, 4, 10, 0)
            }
            statusLayout.addView(tvTime)
            if (isMe) {
                val tvStatus = TextView(context).apply {
                    this.text = if (isSeen) "✓✓" else "✓"
                    textSize = 11f
                    setTextColor(if (isSeen) Color.parseColor("#4FC3F7") else Color.GRAY)
                    setPadding(0, 4, 30, 0)
                }
                statusLayout.addView(tvStatus)
            }
            messageGroup.addView(statusLayout)
        }
        container.addView(messageGroup)
    }

    private fun sendMessage(text: String) {
        val db = FirebaseFirestore.getInstance()
        val counterRef = db.collection("metadata").document("message_counter")

        db.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)
            val nextId = (snapshot.getLong("last_count") ?: 99L) + 1
            val messageData = hashMapOf(
                "sender_id" to myId,
                "receiver_id" to otherUserId,
                "message" to text,
                "timestamp" to FieldValue.serverTimestamp(),
                "message_id" to "message_$nextId",
                "seen" to false
            )
            transaction.set(db.collection("tbl_messages").document("message_$nextId"), messageData)
            transaction.update(counterRef, "last_count", nextId)
        }.addOnSuccessListener { chatInput.text.clear() }
    }

    private fun markMessagesAsSeen() {
        val db = FirebaseFirestore.getInstance()
        db.collection("tbl_messages")
            .whereEqualTo("sender_id", otherUserId)
            .whereEqualTo("receiver_id", myId)
            .whereEqualTo("seen", false)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) return@addOnSuccessListener
                val batch = db.batch()
                docs.forEach { batch.update(it.reference, "seen", true) }
                batch.commit()
            }
    }
}
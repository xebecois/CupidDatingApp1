package com.example.cupiddating

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.widget.NestedScrollView

class LayoutChatDialog(val otherUserId: String, val otherUserName: String, val myId: String) : BottomSheetDialogFragment() {

    private lateinit var container: LinearLayout
    private lateinit var chatInput: EditText
    private lateinit var scrollView: NestedScrollView
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())


    override fun onStart() {
        super.onStart()
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme)
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.let {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)

            // Set the height to 90% of the screen
            val layoutParams = it.layoutParams
            val displayMetrics = resources.displayMetrics
            layoutParams.height = (displayMetrics.heightPixels * 0.9).toInt()
            it.layoutParams = layoutParams

            // Force it to stay expanded and not wrap content
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true

            behavior.isDraggable = true // Ensure the user can still slide it down manually
            it.isClickable = true
            it.isFocusableInTouchMode = true
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.layout_chat_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatInput = view.findViewById(R.id.et_chat_input)
        container = view.findViewById(R.id.LL_chat_messages_container)
        scrollView = view.findViewById(R.id.scroll_chat)
        val tvHeader = view.findViewById<TextView>(R.id.tv_chat_header)
        val etInput = view.findViewById<EditText>(R.id.et_chat_input)
        val btnSend = view.findViewById<ImageButton>(R.id.btn_send_message)

        tvHeader.text = "Chat with $otherUserName"

        markMessagesAsSeen()

        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                etInput.text.clear()
            }
        }

        dialog?.setCanceledOnTouchOutside(false)
        listenForMessages()
    }

    private fun listenForMessages() {
        val db = FirebaseFirestore.getInstance()

        // 1. Remove .orderBy from the query to prevent "null timestamp" hiding.
        // 2. We keep the collection fetch simple.
        // For production, you'd add a .limit(100) here to keep it fast.
        db.collection("tbl_messages")
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                if (!isAdded || view == null) return@addSnapshotListener

                markMessagesAsSeen()

                // Filter and Sort in Kotlin instead of Firestore
                val filteredAndSortedDocs = snapshots!!.documents
                    .filter { doc ->
                        val sId = doc.getString("sender_id") ?: ""
                        val rId = doc.getString("receiver_id") ?: ""
                        (sId == myId && rId == otherUserId) || (sId == otherUserId && rId == myId)
                    }
                    .sortedBy { doc ->
                        doc.getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate() ?: Date()
                    }
                val lastMessageId = filteredAndSortedDocs.lastOrNull()?.id

                container.removeAllViews()
                var lastDateLabel: String? = null

                for (doc in filteredAndSortedDocs) {
                    val sId = doc.getString("sender_id") ?: ""
                    val msg = doc.getString("message") ?: ""
                    val isSeen = doc.getBoolean("seen") ?: false // Get seen status
                    val timestamp = doc.getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate() ?: Date()

                    val isMostRecent = doc.id == lastMessageId

                    // Date Header logic
                    val currentDateLabel = dateFormat.format(timestamp)
                    if (currentDateLabel != lastDateLabel) {
                        addDateHeader(currentDateLabel)
                        lastDateLabel = currentDateLabel
                    }

                    addBubble(msg, sId == myId, timestamp, isSeen, isMostRecent)
                }

                scrollView.post {
                    if (isAdded) {
                        scrollView.smoothScrollTo(0, container.bottom)
                    }
                }
            }
    }

    private fun addDateHeader(dateText: String) {
        val tvDate = TextView(context)
        tvDate.text = dateText
        tvDate.textSize = 12f
        tvDate.setTextColor(Color.GRAY)
        tvDate.gravity = Gravity.CENTER

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 32, 0, 16)
        tvDate.layoutParams = params
        container.addView(tvDate)
    }

    private fun addBubble(text: String, isMe: Boolean, timestamp: Date, isSeen: Boolean, isMostRecent: Boolean) {
        // Parent container to stack the Bubble and the Time Label vertically
        val messageGroup = LinearLayout(context)
        messageGroup.orientation = LinearLayout.VERTICAL

        // 1. The Message Bubble (Your existing logic)
        val tvMsg = TextView(context)
        tvMsg.text = text
        tvMsg.textSize = 16f
        tvMsg.setPadding(40, 20, 40, 20)
        tvMsg.maxWidth = (resources.displayMetrics.widthPixels * 0.7).toInt()

        // Layout for Time and Status Checkmark
        val statusLayout = LinearLayout(context)
        statusLayout.orientation = LinearLayout.HORIZONTAL
        statusLayout.gravity = if (isMe) Gravity.END else Gravity.START

        // 2. The Time Label (New)
        if (isMostRecent) {
            val tvTime = TextView(context)
            tvTime.text = timeFormat.format(timestamp)
            tvTime.textSize = 11f
            tvTime.setTextColor(Color.parseColor("#AAAAAA"))
            tvTime.setPadding(30, 4, 10, 0)
            statusLayout.addView(tvTime)

            if (isMe) {
                val tvStatus = TextView(context)
                tvStatus.text = if (isSeen) "✓✓" else "✓"
                tvStatus.textSize = 11f
                tvStatus.setTextColor(if (isSeen) Color.parseColor("#4FC3F7") else Color.GRAY)
                tvStatus.setPadding(0, 4, 30, 0)
                statusLayout.addView(tvStatus)
            }
        }

        val groupParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        groupParams.setMargins(10, 8, 10, 8)

        if (isMe) {
            groupParams.gravity = Gravity.END
            messageGroup.gravity = Gravity.END
            tvMsg.setBackgroundResource(R.drawable.bg_message_me)
            tvMsg.setTextColor(Color.WHITE)
        } else {
            groupParams.gravity = Gravity.START
            messageGroup.gravity = Gravity.START
            tvMsg.setBackgroundResource(R.drawable.bg_message_them)
            tvMsg.setTextColor(Color.BLACK)
        }

        messageGroup.layoutParams = groupParams
        messageGroup.addView(tvMsg)

        if (isMostRecent) {
            messageGroup.addView(statusLayout)
        }

        container.addView(messageGroup)
    }

    private fun sendMessage(text: String) {
        val db = FirebaseFirestore.getInstance()
        // Reference to a metadata document that tracks the last ID used
        val counterRef = db.collection("metadata").document("message_counter")

        db.runTransaction { transaction ->
            // 1. Get the current count (default to 100 if it doesn't exist)
            val snapshot = transaction.get(counterRef)
            val lastId = snapshot.getLong("last_count") ?: 99L
            val nextId = lastId + 1

            // 2. Prepare the message data
            val messageData = hashMapOf(
                "sender_id" to myId,
                "receiver_id" to otherUserId,
                "message" to text,
                "timestamp" to FieldValue.serverTimestamp(),
                "message_id" to "message_$nextId", // Storing it inside as well for reference
                "seen" to false
            )

            // 3. Create the document with the custom name "message_X"
            val newMessageRef = db.collection("tbl_messages").document("message_$nextId")
            transaction.set(newMessageRef, messageData)

            // 4. Update the counter for the next message
            transaction.update(counterRef, "last_count", nextId)
        }.addOnSuccessListener {
            chatInput.text.clear()
        }.addOnFailureListener { e ->
            Toast.makeText(context, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun markMessagesAsSeen() {
        val db = FirebaseFirestore.getInstance()

        // Find all messages sent by the OTHER user to ME that are currently NOT seen
        db.collection("tbl_messages")
            .whereEqualTo("sender_id", otherUserId)
            .whereEqualTo("receiver_id", myId)
            .whereEqualTo("seen", false)
            .get()
            .addOnSuccessListener { documents ->
                val batch = db.batch()
                for (doc in documents) {
                    batch.update(doc.reference, "seen", true)
                }
                batch.commit()
            }
    }
}
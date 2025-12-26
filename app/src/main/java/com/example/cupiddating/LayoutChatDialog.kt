package com.example.cupiddating

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.*

class LayoutChatDialog(val otherUserId: String, val otherUserName: String, val myId: String) : BottomSheetDialogFragment() {

    private lateinit var container: LinearLayout
    private lateinit var scrollView: ScrollView

    override fun onStart() {
        super.onStart()
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

        dialog?.setCanceledOnTouchOutside(false)

        container = view.findViewById(R.id.LL_chat_messages_container)
        scrollView = view.findViewById(R.id.scroll_chat)
        val tvHeader = view.findViewById<TextView>(R.id.tv_chat_header)
        val etInput = view.findViewById<EditText>(R.id.et_chat_input)
        val btnSend = view.findViewById<ImageButton>(R.id.btn_send_message)

        tvHeader.text = "Chat with $otherUserName"

        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                etInput.text.clear()
            }
        }

        listenForMessages()
    }

    private fun listenForMessages() {
        val db = FirebaseFirestore.getInstance()

        // Real-time listener for the messages collection
        db.collection("tbl_messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener

                container.removeAllViews() // Clear for fresh render

                for (doc in snapshots!!.documents) {
                    val sId = doc.getString("sender_id") ?: ""
                    val rId = doc.getString("receiver_id") ?: ""
                    val msg = doc.getString("message") ?: ""

                    // Filter: Only show messages belonging to THIS conversation
                    if ((sId == myId && rId == otherUserId) || (sId == otherUserId && rId == myId)) {
                        addBubble(msg, sId == myId)
                    }
                }

                // Auto-scroll to the bottom of the chat
                scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
            }
    }

    private fun addBubble(text: String, isMe: Boolean) {
        val tv = TextView(context)
        tv.text = text
        tv.textSize = 16f
        tv.setPadding(40, 20, 40, 20)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = 8
        params.bottomMargin = 8

        if (isMe) {
            params.gravity = Gravity.END
            tv.setBackgroundResource(R.drawable.bg_message_me) // Create this drawable
            tv.setTextColor(Color.WHITE)
        } else {
            params.gravity = Gravity.START
            tv.setBackgroundResource(R.drawable.bg_message_them) // Create this drawable
            tv.setTextColor(Color.BLACK)
        }

        tv.layoutParams = params
        container.addView(tv)
    }

    private fun sendMessage(text: String) {
        val db = FirebaseFirestore.getInstance()
        val messageData = hashMapOf(
            "sender_id" to myId,
            "receiver_id" to otherUserId,
            "message" to text,
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("tbl_messages").add(messageData)
    }
}
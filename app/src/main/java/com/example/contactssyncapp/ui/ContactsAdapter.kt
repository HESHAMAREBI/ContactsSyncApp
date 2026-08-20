package com.example.contactssyncapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.contactssyncapp.R
import com.example.contactssyncapp.data.Contact
import com.example.contactssyncapp.data.PhoneUtils
import com.google.android.material.button.MaterialButton

class ContactsAdapter(
    private val onCallClick: (String) -> Unit,
    private val onCopyClick: (String) -> Unit
) : ListAdapter<Contact, ContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact_card, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position), onCallClick, onCopyClick)
    }

    class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAvatarInitial: TextView = itemView.findViewById(R.id.tvAvatarInitial)
        private val tvContactName: TextView = itemView.findViewById(R.id.tvContactName)
        private val tvJobTitle: TextView = itemView.findViewById(R.id.tvJobTitle)
        private val tvPhoneNumber: TextView = itemView.findViewById(R.id.tvPhoneNumber)
        private val tvDepartment: TextView = itemView.findViewById(R.id.tvDepartment)
        private val departmentContainer: View = itemView.findViewById(R.id.departmentContainer)
        private val btnCall: MaterialButton = itemView.findViewById(R.id.btnCall)
        private val btnCopy: MaterialButton = itemView.findViewById(R.id.btnCopy)

        fun bind(
            contact: Contact,
            onCallClick: (String) -> Unit,
            onCopyClick: (String) -> Unit
        ) {
            // Name & Avatar Initial
            val displayName = contact.name.trim().ifBlank { "جهة اتصال بدون اسم" }
            tvContactName.text = displayName
            tvAvatarInitial.text = extractArabicInitial(displayName)

            // Job Title
            val title = contact.jobTitle.trim().ifEmpty { contact.notes.trim() }
            if (title.isNotEmpty() && title != contact.department.trim() && title != contact.address.trim()) {
                tvJobTitle.text = title
                tvJobTitle.visibility = View.VISIBLE
            } else {
                tvJobTitle.visibility = View.GONE
            }

            // Department / Branch
            val department = contact.department.trim().ifEmpty { contact.address.trim() }
            if (department.isNotEmpty()) {
                tvDepartment.text = department
                departmentContainer.visibility = View.VISIBLE
            } else {
                departmentContainer.visibility = View.GONE
            }

            // Phone resolution
            val cleanPhone = PhoneUtils.extractCleanPhone(contact.phone)
            val displayPhone = PhoneUtils.formatPhoneForDisplay(contact.phone)

            if (cleanPhone.isNotEmpty()) {
                tvPhoneNumber.text = displayPhone
                btnCall.isEnabled = true
                btnCopy.isEnabled = true
            } else {
                tvPhoneNumber.text = "لا يوجد رقم"
                btnCall.isEnabled = false
                btnCopy.isEnabled = false
            }

            // Action Handlers
            btnCall.setOnClickListener {
                if (cleanPhone.isNotEmpty()) {
                    onCallClick(cleanPhone)
                }
            }

            btnCopy.setOnClickListener {
                if (cleanPhone.isNotEmpty()) {
                    onCopyClick(cleanPhone)
                }
            }
        }

        private fun extractArabicInitial(name: String): String {
            val cleaned = name.trim()
            if (cleaned.isEmpty() || cleaned.startsWith("c-", ignoreCase = true)) return "م"
            return cleaned.first().toString()
        }
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem.contactId == newItem.contactId ||
                    (oldItem.name.trim() == newItem.name.trim() && oldItem.phone.trim() == newItem.phone.trim())
        }

        override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem == newItem
        }
    }
}

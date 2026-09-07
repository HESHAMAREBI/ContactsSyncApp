package com.example.contactssyncapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.contactssyncapp.data.Contact
import com.example.contactssyncapp.databinding.ItemContactCardBinding

class ContactsAdapter(
    private val onCallClick: ((String) -> Unit)? = null,
    private val onCopyClick: ((String) -> Unit)? = null
) : ListAdapter<Contact, ContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position), onCallClick, onCopyClick)
    }

    class ContactViewHolder(private val binding: ItemContactCardBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            contact: Contact,
            onCallClick: ((String) -> Unit)?,
            onCopyClick: ((String) -> Unit)?
        ) {
            val context = itemView.context

            // 1. Full Arabic Name & Initial
            val displayName = contact.name.trim().ifBlank { "جهة اتصال غير محددة" }
            binding.tvContactName.text = displayName
            binding.tvAvatarInitial.text = extractArabicInitial(displayName)

            // 2. Department & Job Title Tags
            val department = contact.department.trim().ifEmpty { contact.address.trim() }
            if (department.isNotEmpty()) {
                binding.tvDepartment.text = department
                binding.departmentContainer.visibility = View.VISIBLE
            } else {
                binding.departmentContainer.visibility = View.GONE
            }

            val jobTitle = contact.jobTitle.trim()
            if (jobTitle.isNotEmpty() && jobTitle != department) {
                binding.tvJobTitle.text = jobTitle
                binding.jobTitleBadgeContainer.visibility = View.VISIBLE
            } else {
                binding.jobTitleBadgeContainer.visibility = View.GONE
            }

            binding.badgesContainer.visibility = if (department.isNotEmpty() || (jobTitle.isNotEmpty() && jobTitle != department)) {
                View.VISIBLE
            } else {
                View.GONE
            }

            // 3. Phone Number & Direct Actions
            val phone = contact.phone.trim()
            if (phone.isNotEmpty()) {
                binding.tvPhoneNumber.text = phone
                binding.btnCall.isEnabled = true
                binding.btnCall.alpha = 1.0f
                binding.btnCopy.isEnabled = true
                binding.btnCopy.alpha = 1.0f

                binding.btnCall.setOnClickListener {
                    try {
                        val dialNumber = phone.replace(" ", "")
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialNumber"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "تعذر فتح تطبيق الهاتف لإجراء المكالمة", Toast.LENGTH_SHORT).show()
                    }
                    onCallClick?.invoke(phone)
                }

                binding.btnCopy.setOnClickListener {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Contact Phone", phone)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "تم نسخ الرقم: $phone", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "تعذر نسخ الرقم", Toast.LENGTH_SHORT).show()
                    }
                    onCopyClick?.invoke(phone)
                }
            } else {
                binding.tvPhoneNumber.text = "لا يوجد رقم مسجل"
                binding.btnCall.isEnabled = false
                binding.btnCall.alpha = 0.5f
                binding.btnCopy.isEnabled = false
                binding.btnCopy.alpha = 0.5f
                binding.btnCall.setOnClickListener(null)
                binding.btnCopy.setOnClickListener(null)
            }
        }

        private fun extractArabicInitial(name: String): String {
            val cleaned = name.trim()
            val firstChar = cleaned.firstOrNull { it.isLetter() }
            return firstChar?.uppercase() ?: "م"
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

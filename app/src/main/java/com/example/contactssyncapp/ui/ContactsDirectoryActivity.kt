package com.example.contactssyncapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.contactssyncapp.R
import kotlinx.coroutines.launch

class ContactsDirectoryActivity : AppCompatActivity() {

    private val viewModel: ContactsDirectoryViewModel by viewModels()

    private lateinit var backButton: ImageButton
    private lateinit var contactCountText: TextView
    private lateinit var searchEditText: EditText
    private lateinit var clearSearchButton: ImageButton
    private lateinit var contactsRecyclerView: RecyclerView
    private lateinit var directoryProgressBar: ProgressBar
    private lateinit var emptyStateContainer: View
    private lateinit var emptyStateTitle: TextView
    private lateinit var emptyStateMessage: TextView

    private lateinit var refreshSyncButton: ImageButton
    private lateinit var contactsAdapter: ContactsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts_directory)

        initViews()
        setupRecyclerView()
        setupListeners()
        observeUiState()

        viewModel.loadContacts()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        refreshSyncButton = findViewById(R.id.refreshSyncButton)
        contactCountText = findViewById(R.id.contactCountText)
        searchEditText = findViewById(R.id.searchEditText)
        clearSearchButton = findViewById(R.id.clearSearchButton)
        contactsRecyclerView = findViewById(R.id.contactsRecyclerView)
        directoryProgressBar = findViewById(R.id.directoryProgressBar)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        emptyStateTitle = findViewById(R.id.emptyStateTitle)
        emptyStateMessage = findViewById(R.id.emptyStateMessage)
    }

    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter(
            onCallClick = { phone ->
                initiatePhoneCall(phone)
            },
            onCopyClick = { phone ->
                copyPhoneToClipboard(phone)
            }
        )

        contactsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ContactsDirectoryActivity)
            adapter = contactsAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }

        refreshSyncButton.setOnClickListener {
            Toast.makeText(this, "جاري تحديث ومزامنة البيانات مع Google Sheets...", Toast.LENGTH_SHORT).show()
            viewModel.syncFreshContacts()
        }

        searchEditText.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            clearSearchButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            viewModel.onSearchQueryChanged(query)
        }

        clearSearchButton.setOnClickListener {
            searchEditText.text?.clear()
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ContactsDirectoryUiState.Loading -> {
                            directoryProgressBar.visibility = View.VISIBLE
                            contactsRecyclerView.visibility = View.GONE
                            emptyStateContainer.visibility = View.GONE
                            contactCountText.text = "جاري تحميل السجلات..."
                        }
                        is ContactsDirectoryUiState.Success -> {
                            directoryProgressBar.visibility = View.GONE

                            if (state.totalCount > 0) {
                                contactCountText.text = "إجمالي جهات الاتصال: ${state.totalCount} (المعروض: ${state.filteredList.size})"
                            } else {
                                contactCountText.text = "لا توجد جهات اتصال مزامنة"
                            }

                            contactsAdapter.submitList(state.filteredList)

                            if (state.filteredList.isEmpty()) {
                                contactsRecyclerView.visibility = View.GONE
                                emptyStateContainer.visibility = View.VISIBLE

                                if (state.query.isNotEmpty()) {
                                    emptyStateTitle.text = "لا توجد نتائج مطابقة لـ \"${state.query}\""
                                    emptyStateMessage.text = "جرّب البحث بالاسم أو رقم الهاتف أو اسم الإدارة"
                                } else {
                                    emptyStateTitle.text = "دليل الهاتف فارغ"
                                    emptyStateMessage.text = "يرجى الرجوع والضغط على 'مزامنة جهات الاتصال الآن' لتحميل السجلات"
                                }
                            } else {
                                contactsRecyclerView.visibility = View.VISIBLE
                                emptyStateContainer.visibility = View.GONE
                            }
                        }
                        is ContactsDirectoryUiState.Error -> {
                            directoryProgressBar.visibility = View.GONE
                            contactsRecyclerView.visibility = View.GONE
                            emptyStateContainer.visibility = View.VISIBLE
                            emptyStateTitle.text = "تعذر تحميل البيانات"
                            emptyStateMessage.text = state.message
                            contactCountText.text = "خطأ في التحميل"
                        }
                    }
                }
            }
        }
    }

    private fun initiatePhoneCall(phone: String) {
        val clean = com.example.contactssyncapp.data.PhoneUtils.extractCleanPhone(phone)
        if (clean.isEmpty()) {
            Toast.makeText(this, "لا يوجد رقم هاتف صالح للاتصال", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(clean)}")
            }
            startActivity(dialIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر فتح تطبيق الهاتف لإجراء المكالمة", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyPhoneToClipboard(phone: String) {
        val clean = com.example.contactssyncapp.data.PhoneUtils.extractCleanPhone(phone)
        if (clean.isEmpty()) {
            Toast.makeText(this, "لا يوجد رقم صالح للنسخ", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Corporate Contact Phone", clean)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "تم نسخ الرقم: $clean", Toast.LENGTH_SHORT).show()
    }
}

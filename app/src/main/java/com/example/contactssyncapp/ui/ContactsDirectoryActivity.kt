package com.example.contactssyncapp.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.contactssyncapp.data.ContactsSyncRepository
import com.example.contactssyncapp.data.GoogleSheetsRepository
import com.example.contactssyncapp.databinding.ActivityContactsDirectoryBinding
import kotlinx.coroutines.launch

class ContactsDirectoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsDirectoryBinding
    private lateinit var contactsAdapter: ContactsAdapter

    private val viewModel: ContactsDirectoryViewModel by viewModels {
        ContactsDirectoryViewModelFactory(
            ContactsSyncRepository(applicationContext),
            GoogleSheetsRepository(applicationContext)
        )
    }

    companion object {
        private const val TAG = "ContactsDirectory"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsDirectoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        observeUiState()

        viewModel.loadContacts()
    }

    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter()

        binding.contactsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ContactsDirectoryActivity)
            adapter = contactsAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.refreshSyncButton.setOnClickListener {
            Toast.makeText(this, "جاري تحديث ومزامنة البيانات مع Google Sheets...", Toast.LENGTH_SHORT).show()
            viewModel.syncFreshContacts()
        }

        binding.searchEditText.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            binding.clearSearchButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            viewModel.onSearchQueryChanged(query)
        }

        binding.clearSearchButton.setOnClickListener {
            binding.searchEditText.text?.clear()
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ContactsDirectoryUiState.Loading -> {
                            binding.directoryProgressBar.visibility = View.VISIBLE
                            binding.contactsRecyclerView.visibility = View.GONE
                            binding.emptyStateContainer.visibility = View.GONE
                            binding.contactCountText.text = "جاري تحميل السجلات..."
                        }
                        is ContactsDirectoryUiState.Success -> {
                            binding.directoryProgressBar.visibility = View.GONE

                            if (state.totalCount > 0) {
                                binding.contactCountText.text = if (state.query.isEmpty()) {
                                    "إجمالي جهات الاتصال: ${state.totalCount}"
                                } else {
                                    "إجمالي جهات الاتصال: ${state.totalCount} (المعروض: ${state.filteredList.size})"
                                }
                            } else {
                                binding.contactCountText.text = "لا توجد جهات اتصال مزامنة"
                            }

                            contactsAdapter.submitList(state.filteredList)

                            if (state.filteredList.isEmpty()) {
                                binding.contactsRecyclerView.visibility = View.GONE
                                binding.emptyStateContainer.visibility = View.VISIBLE

                                if (state.query.isNotEmpty()) {
                                    binding.emptyStateTitle.text = "لا توجد نتائج مطابقة لـ \"${state.query}\""
                                    binding.emptyStateMessage.text = "جرّب البحث بالاسم أو رقم الهاتف أو اسم الإدارة"
                                } else {
                                    binding.emptyStateTitle.text = "دليل الهاتف فارغ"
                                    binding.emptyStateMessage.text = "يرجى الرجوع والضغط على 'مزامنة جهات الاتصال الآن' لتحميل السجلات"
                                }
                            } else {
                                binding.contactsRecyclerView.visibility = View.VISIBLE
                                binding.emptyStateContainer.visibility = View.GONE
                            }
                        }
                        is ContactsDirectoryUiState.Error -> {
                            binding.directoryProgressBar.visibility = View.GONE
                            binding.contactsRecyclerView.visibility = View.GONE
                            binding.emptyStateContainer.visibility = View.VISIBLE
                            binding.emptyStateTitle.text = "تعذر تحميل البيانات"
                            binding.emptyStateMessage.text = state.message
                            binding.contactCountText.text = "خطأ في التحميل"
                        }
                    }
                }
            }
        }
    }
}

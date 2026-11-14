package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Conversation
import com.example.data.model.CountryCode
import com.example.data.model.CountryCodes
import com.example.data.model.PhoneDirectoryEntry
import com.example.data.model.SavedContact
import com.example.data.repository.LookupContactResult
import com.example.data.repository.MessagingRepository
import com.example.util.ContactSyncHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NewChatViewModel(
    private val messagingRepo: MessagingRepository,
    val currentUid: String
) : ViewModel() {

    private val _selectedCountry = MutableStateFlow(CountryCodes.defaultCountry)
    val selectedCountry: StateFlow<CountryCode> = _selectedCountry.asStateFlow()

    private val _phoneInput = MutableStateFlow("")
    val phoneInput: StateFlow<String> = _phoneInput.asStateFlow()

    private val _lookupResult = MutableStateFlow<LookupContactResult?>(null)
    val lookupResult: StateFlow<LookupContactResult?> = _lookupResult.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isSyncingContacts = MutableStateFlow(false)
    val isSyncingContacts: StateFlow<Boolean> = _isSyncingContacts.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun selectCountry(country: CountryCode) {
        _selectedCountry.value = country
    }

    fun updatePhoneInput(input: String) {
        _phoneInput.value = input.take(22)
        _statusMessage.value = null
    }

    fun searchPhoneNumber() {
        val raw = _phoneInput.value.trim()
        if (raw.isEmpty()) {
            _statusMessage.value = "Enter a full phone number to find a user."
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            _statusMessage.value = null
            val res = messagingRepo.lookupRegisteredUserByPhone(raw, _selectedCountry.value.dialCode)
            _isSearching.value = false
            res.onSuccess { result ->
                _lookupResult.value = result
                if (result.registeredEntry == null) {
                    _statusMessage.value = "${result.phoneE164} is not on SEPTIS yet. You can invite them below."
                }
            }.onFailure {
                _statusMessage.value = it.localizedMessage ?: "Enter a valid phone number."
            }
        }
    }

    fun syncContacts(context: Context) {
        viewModelScope.launch {
            _isSyncingContacts.value = true
            _statusMessage.value = null
            val deviceContacts = ContactSyncHelper.readDeviceContacts(context, _selectedCountry.value.dialCode)
            if (deviceContacts.isEmpty()) {
                _isSyncingContacts.value = false
                _statusMessage.value = "No phone contacts found on device."
                return@launch
            }
            val syncRes = messagingRepo.syncDeviceContacts(deviceContacts)
            _isSyncingContacts.value = false
            syncRes.onSuccess { matched ->
                _statusMessage.value = if (matched.isEmpty()) {
                    "Scanned ${deviceContacts.size} contacts — none are on SEPTIS yet."
                } else {
                    "Synced ${matched.size} SEPTIS contact(s) from your address book."
                }
            }.onFailure {
                _statusMessage.value = it.localizedMessage ?: "Contact sync failed."
            }
        }
    }

    fun startChatWithDirectoryEntry(entry: PhoneDirectoryEntry, onOpened: (Conversation) -> Unit) {
        if (entry.uid == currentUid) {
            _statusMessage.value = "That is your own SEPTIS phone number."
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            val res = messagingRepo.getOrCreateDirectConversation(entry)
            _isSearching.value = false
            res.onSuccess(onOpened)
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Unable to open conversation" }
        }
    }

    fun startChatWithSavedContact(contact: SavedContact, onOpened: (Conversation) -> Unit) {
        startChatWithDirectoryEntry(
            PhoneDirectoryEntry(
                id = contact.phoneE164,
                phoneKey = contact.phoneE164,
                uid = contact.contactUid,
                phoneE164 = contact.phoneE164,
                displayName = contact.localDeviceName ?: contact.displayName,
                about = contact.about.orEmpty(),
                photoBlobId = contact.photoBlobId
            ),
            onOpened
        )
    }
}

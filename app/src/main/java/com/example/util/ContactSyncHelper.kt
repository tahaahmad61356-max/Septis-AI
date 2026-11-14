package com.example.util

import android.content.Context
import android.provider.ContactsContract
import com.example.data.model.PhoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceContact(
    val displayName: String,
    val rawPhone: String,
    val normalizedE164: String
)

object ContactSyncHelper {
    suspend fun readDeviceContacts(
        context: Context,
        defaultCountryCode: String = "+92"
    ): List<DeviceContact> = withContext(Dispatchers.IO) {
        val seenE164 = LinkedHashMap<String, DeviceContact>()
        val resolver = context.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        try {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = if (nameCol >= 0) cursor.getString(nameCol).orEmpty().trim() else ""
                    val rawNum = if (numCol >= 0) cursor.getString(numCol).orEmpty().trim() else ""
                    val e164 = PhoneUtils.normalizeToE164(rawNum, defaultCountryCode)
                    if (e164 != null && !seenE164.containsKey(e164)) {
                        seenE164[e164] = DeviceContact(
                            displayName = name.ifBlank { e164 },
                            rawPhone = rawNum,
                            normalizedE164 = e164
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        seenE164.values.toList()
    }
}

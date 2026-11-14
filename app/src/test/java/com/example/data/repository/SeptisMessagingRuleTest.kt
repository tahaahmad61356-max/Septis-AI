package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.PhoneUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SeptisMessagingRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun registerPhoneAndLookup_enforcesUniquenessAndExactDiscovery() = runBlocking {
        val uniqueSuffix = (100000..999999).random()
        val phoneE164 = "+92300$uniqueSuffix"

        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@septis.test")
        println("DEBUG_RULE: aliceUid=$aliceUid authCurrent=${auth.currentUser?.uid} appOpts=${firestore.app.options.projectId}")
        val aliceRepo = MessagingRepository(firestore, auth)

        val claim1 = withTimeout(5000) {
            aliceRepo.registerOrUpdatePhoneProfile(
                phoneE164 = phoneE164,
                countryCode = "+92",
                nationalNumber = "300$uniqueSuffix",
                displayName = "Alice Tester"
            ).getOrElse { throw AssertionError("Step claim1 failed: ${it.message}", it) }
        }
        assertTrue(claim1 is PhoneClaimResult.Success)

        val bobUid = signInTestUser("bob_${UUID.randomUUID()}@septis.test")
        val bobRepo = MessagingRepository(firestore, auth)

        // Bob cannot claim Alice's already-registered phone number
        val claim2 = withTimeout(5000) {
            bobRepo.registerOrUpdatePhoneProfile(
                phoneE164 = phoneE164,
                countryCode = "+92",
                nationalNumber = "300$uniqueSuffix",
                displayName = "Bob Impostor"
            ).getOrElse { throw AssertionError("Step claim2 failed: ${it.message}", it) }
        }
        assertTrue(claim2 is PhoneClaimResult.AlreadyClaimed)

        // Bob CAN look up Alice by exact phone number
        val lookup = withTimeout(5000) {
            bobRepo.lookupRegisteredUserByPhone(phoneE164, "+92")
                .getOrElse { throw AssertionError("Step lookup failed: ${it.message}", it) }
        }
        assertNotNull(lookup.registeredEntry)
        assertEquals(aliceUid, lookup.registeredEntry?.uid)
        assertEquals("Alice Tester", lookup.registeredEntry?.displayName)
        assertTrue(bobUid != aliceUid)
    }

    @Test
    fun directMessaging_syncsMessagesAndIsolatesNonParticipants() = runBlocking {
        val aliceNum = "311${(100000..999999).random()}"
        val bobNum = "322${(100000..999999).random()}"

        val aliceUid = signInTestUser("alice_dm_${UUID.randomUUID()}@septis.test")
        val aliceRepo = MessagingRepository(firestore, auth)
        withTimeout(5000) {
            aliceRepo.registerOrUpdatePhoneProfile("+92$aliceNum", "+92", aliceNum, "Alice DM").getOrThrow()
        }

        val bobEmail = "bob_dm_${UUID.randomUUID()}@septis.test"
        val bobUid = signInTestUser(bobEmail)
        val bobRepo = MessagingRepository(firestore, auth)
        withTimeout(5000) {
            bobRepo.registerOrUpdatePhoneProfile("+92$bobNum", "+92", bobNum, "Bob DM").getOrThrow()
        }

        val aliceDir = withTimeout(5000) {
            bobRepo.lookupRegisteredUserByPhone("+92$aliceNum", "+92").getOrThrow().registeredEntry!!
        }

        val conv = withTimeout(5000) { bobRepo.getOrCreateDirectConversation(aliceDir).getOrThrow() }
        val sentMsg = withTimeout(5000) {
            bobRepo.sendMessage(conv, "Bob DM", type = "text", text = "Salam Alice!").getOrThrow()
        }

        // Non-participant Eve cannot read Alice/Bob conversation messages
        signInTestUser("eve_${UUID.randomUUID()}@septis.test")
        val eveRepo = MessagingRepository(firestore, auth)
        var crossReadDenied = false
        try {
            withTimeout(3000) {
                eveRepo.observeMessages(conv.id, aliceUid).first()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                crossReadDenied = true
            }
        }
        assertTrue("Non-participant Eve must receive PERMISSION_DENIED reading Alice/Bob chat", crossReadDenied)

        // Verify Bob can read the live message stream as a participant
        val bobCheckUid = signInTestUser(bobEmail)
        assertEquals(bobUid, bobCheckUid)
        val bobMsgs = withTimeout(3000) {
            bobRepo.observeMessages(conv.id, bobUid).first { list -> list.any { it.id == sentMsg.id } }
        }
        assertTrue(bobMsgs.any { it.text == "Salam Alice!" })
        assertTrue(conv.id == PhoneUtils.generateDirectConversationId(aliceUid, bobUid))
    }

    @Test
    fun unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        kotlinx.coroutines.delay(150L)
        val repo = MessagingRepository(firestore, auth)
        var denied = false
        try {
            withTimeout(3000) {
                repo.observeConversations("any_uid").first()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                denied = true
            }
        }
        assertTrue("Unauthenticated conversation query must fail with PERMISSION_DENIED", denied)
    }

    @Test
    fun ordinaryUser_cannotReadAdminVaultOrModifyGlobalSettings() = runBlocking {
        signInTestUser("ordinary_${UUID.randomUUID()}@septis.test")
        val adminRepo = AdminRepository(firestore, auth)

        val check = withTimeout(5000) { adminRepo.checkAdminAuthorization().getOrThrow() }
        assertTrue(check is AdminVaultCheckResult.Unauthorized)

        val settingsResult = withTimeout(5000) {
            adminRepo.updateAppSettings("Hacked", true, true, true)
        }
        assertTrue(settingsResult.isFailure)
        val fsEx = settingsResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }
}

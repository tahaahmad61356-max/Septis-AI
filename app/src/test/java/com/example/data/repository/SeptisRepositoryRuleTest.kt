package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SeptisRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserAccount_andUpdateProfile_succeedsForAuthenticatedUser() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = SeptisRepository(firestore, auth)

        val accountResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserAccount(uid, ALICE_EMAIL, "Alice Member")
        }
        assertTrue(accountResult.isSuccess)
        val account = accountResult.getOrThrow()
        assertEquals("user", account.role)
        assertEquals("active", account.status)

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateMyProfile(uid, "Alice Updated", "Engineering lead", "Engineering", "+15550101")
        }
        assertTrue(updateResult.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMyAccount(uid).first { it?.displayName == "Alice Updated" }
        }
        assertEquals("Engineering", observed?.department)
    }

    @Test
    fun updateAndVerifyPasscode_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = SeptisRepository(firestore, auth)

        val setResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateMyPasscode(uid, "8492")
        }
        assertTrue(setResult.isSuccess)
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { repository.hasPasscodeSet(uid) })
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { repository.verifyMyPasscode(uid, "8492").getOrThrow() })
        assertFalse(withTimeout(DEFAULT_TIMEOUT_MS) { repository.verifyMyPasscode(uid, "0000").getOrThrow() })
    }

    @Test
    fun createAndObserveSharedRecord_succeedsForActiveUser() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = SeptisRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserAccount(uid, ALICE_EMAIL, "Alice").getOrThrow()
        }

        val uniqueTitle = "SEPTIS Update ${UUID.randomUUID().toString().take(8)}"
        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createRecord(
                title = uniqueTitle,
                content = "Sharing project milestone with the team.",
                category = "share",
                authorName = "Alice"
            )
        }
        assertTrue(createResult.isSuccess)
        val recordId = createResult.getOrThrow()

        val records = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeSharedRecords().first { list -> list.any { it.id == recordId } }
        }
        assertTrue(records.any { it.title == uniqueTitle })
    }

    @Test
    fun crossUserAccountRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SeptisRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserAccount(aliceUid, ALICE_EMAIL, "Alice").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = SeptisRepository(firestore, auth)
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeMyAccount(aliceUid).first()
            }
        } catch (e: Throwable) {
            caught = e
        }
        val firestoreEx = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun ordinaryUser_cannotAccessAdminVaultOrPublishAnnouncements() = runBlocking {
        signInTestUser(BOB_EMAIL)
        val bobRepo = SeptisRepository(firestore, auth)

        val adminCheck = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.checkAdminAuthorization().getOrThrow()
        }
        assertTrue(adminCheck is AdminVaultCheckResult.Unauthorized)

        val annResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.saveAnnouncementByAdmin(
                id = null,
                title = "Unauthorized",
                body = "Should be denied",
                priority = "urgent",
                pinned = true,
                visibility = "all",
                authorName = "Bob"
            )
        }
        assertTrue(annResult.isFailure)
        val firestoreEx = checkNotNull(annResult.exceptionOrNull()?.unwrapFirestoreException())
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun unauthenticatedCaller_cannotObserveAnnouncements() = runBlocking {
        auth.signOut()
        val repo = SeptisRepository(firestore, auth)
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observePublishedAnnouncements().first()
            }
        } catch (e: Throwable) {
            caught = e
        }
        assertTrue(caught is IllegalStateException || caught?.unwrapFirestoreException() != null)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_septis@test.com"
        const val BOB_EMAIL = "bob_septis@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}

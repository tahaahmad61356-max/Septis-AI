const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const nowTs = () => new Date();

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const EVE_UID = "eve_uid_003";
const OWNER_UID = "owner_uid_999";
const OWNER_EMAIL = "tahaahmad61357@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserPayload(uid, phoneE164, displayName) {
  return {
    userId: uid,
    phoneE164,
    countryCode: "+1",
    nationalNumber: phoneE164.replace("+1", ""),
    displayName,
    about: "Available on SEPTIS",
    role: "user",
    status: "active",
    isOnline: true,
    privacyLastSeen: "everyone",
    privacyPhoto: "everyone",
    privacyAbout: "everyone",
    readReceiptsEnabled: true,
    canEditProfile: true,
    canPostContent: true,
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };
}

function validDirectoryPayload(phoneKey, uid, phoneE164, displayName) {
  return {
    phoneKey,
    uid,
    phoneE164,
    displayName,
    about: "Available on SEPTIS",
    isOnline: true,
    privacyLastSeen: "everyone",
    privacyPhoto: "everyone",
    privacyAbout: "everyone",
    status: "active",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated access is denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("phone_directory").doc("p_15551112222").get());
  await assertFails(unauthDb.collection("conversations").get());
  await assertFails(unauthDb.collection("admin_vault").doc("config").get());
});

test("User cannot self-elevate role or read another user's private profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "+15551112222", "Alice"))
  );

  // Shadow privilege escalation attempt
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: nowTs(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
});

test("Phone directory allows exact single-number lookup but blocks bulk scraping", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("phone_directory").doc("p_15551112222").set(
      validDirectoryPayload("p_15551112222", ALICE_UID, "+15551112222", "Alice")
    )
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("phone_directory").doc("p_15551112222").get());
  await assertFails(bobDb.collection("phone_directory").get());
});

test("Participants can chat and mark read receipts while outsiders are denied", async () => {
  const convId = "dm_alice_bob";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      type: "direct",
      participantUids: [ALICE_UID, BOB_UID],
      adminUids: [],
      title: "",
      onlyAdminsCanSend: false,
      onlyAdminsCanEditInfo: false,
      lastMessageText: "Hello Bob",
      lastMessageType: "text",
      lastMessageSenderUid: ALICE_UID,
      lastMessageAt: nowTs(),
      typingUids: [],
      createdBy: ALICE_UID,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  const msgRef = aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      conversationId: convId,
      senderUid: ALICE_UID,
      senderName: "Alice",
      participantUids: [ALICE_UID, BOB_UID],
      type: "text",
      text: "Hello Bob",
      edited: false,
      deletedForEveryone: false,
      deletedForUids: [],
      deliveredToUids: [ALICE_UID],
      readByUids: [ALICE_UID],
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Bob queries messages with participantUids filter and marks read
  await assertSucceeds(
    bobDb
      .collection("conversations")
      .doc(convId)
      .collection("messages")
      .where("participantUids", "array-contains", BOB_UID)
      .get()
  );
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").update({
      deliveredToUids: [ALICE_UID, BOB_UID],
      readByUids: [ALICE_UID, BOB_UID],
      updatedAt: nowTs(),
    })
  );

  // Bob cannot alter Alice's message text
  await assertFails(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").update({
      text: "Tampered",
      edited: true,
      updatedAt: nowTs(),
    })
  );

  // Outsider Eve cannot read or write
  await assertFails(eveDb.collection("conversations").doc(convId).get());
  await assertFails(
    eveDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").get()
  );
});

test("Email spoofing cannot gain admin access, while verified root admin succeeds", async () => {
  const spoofedDb = testEnv
    .authenticatedContext("spoof_1", { email: OWNER_EMAIL, email_verified: false })
    .firestore();
  await assertFails(spoofedDb.collection("admin_vault").doc("config").get());
  await assertFails(spoofedDb.collection("reports").get());

  const adminDb = testEnv
    .authenticatedContext(OWNER_UID, { email: OWNER_EMAIL, email_verified: true })
    .firestore();
  await assertSucceeds(
    adminDb.collection("app_settings").doc("global").set({
      appBannerMessage: "Welcome to SEPTIS",
      allowUserPosting: true,
      allowUserProfileEdits: true,
      maintenanceMode: false,
      updatedBy: OWNER_UID,
      updatedAt: nowTs(),
    })
  );
});

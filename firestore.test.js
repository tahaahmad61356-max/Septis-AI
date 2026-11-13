const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const crypto = require("node:crypto");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_owner_001";
const ALICE_UID = "alice_user_001";
const BOB_UID = "bob_user_002";
const OWNER_EMAIL = "tahaahmad61357@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function sha256Hex(input) {
  return crypto.createHash("sha256").update(input).digest("hex");
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

test("Unauthenticated user: rejected from reading users, announcements, records, notifications, admin_vault", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("announcements").where("visibility", "==", "all").get());
  await assertFails(unauthDb.collection("records").where("visibility", "==", "all").get());
  await assertFails(unauthDb.collection("notifications").where("recipientUid", "==", "all").get());
  await assertFails(unauthDb.collection("admin_vault").doc("config").get());
  await assertFails(unauthDb.collection("admins").get());
});

test("Email spoofing test: unverified owner email cannot act as bootstrap admin", async () => {
  const spoofedDb = testEnv.authenticatedContext("spoof_uid", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(spoofedDb.collection("admin_vault").doc("config").get());
  await assertFails(spoofedDb.collection("users").get());
});

test("Bootstrap verified admin: can configure admin_vault and verify matching Admin Access Code session", async () => {
  const ownerDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();

  const salt = "0123456789abcdef0123456789abcdef";
  const secretCode = "TestVaultCode#99";
  const validHash = sha256Hex(salt + secretCode);
  const wrongHash = sha256Hex(salt + "WrongCode");
  const now = new Date();

  await assertSucceeds(
    ownerDb.collection("admin_vault").doc("config").set({
      salt,
      codeHash: validHash,
      updatedBy: ADMIN_UID,
      updatedAt: now,
    })
  );

  // Wrong code hash must be rejected by backend rules
  await assertFails(
    ownerDb.collection("admin_sessions").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      submittedHash: wrongHash,
      deviceLabel: "Pixel Test",
      verifiedAt: now,
    })
  );

  // Matching code hash succeeds
  await assertSucceeds(
    ownerDb.collection("admin_sessions").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      submittedHash: validHash,
      deviceLabel: "Pixel Test",
      verifiedAt: now,
    })
  );
});

test("Ordinary user: cannot read admin_vault, cannot promote self to admin, cannot read another user's PII", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("admin_vault").doc("config").set({
      salt: "0123456789abcdef0123456789abcdef",
      codeHash: sha256Hex("secret"),
      updatedBy: ADMIN_UID,
      updatedAt: now,
    });
    await db.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: "bob@test.com",
      displayName: "Bob",
      role: "user",
      status: "active",
      canEditProfile: true,
      canPostContent: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@test.com",
    email_verified: true,
  }).firestore();

  // Cannot read admin vault or admins
  await assertFails(aliceDb.collection("admin_vault").doc("config").get());
  await assertFails(aliceDb.collection("admins").doc(ALICE_UID).set({
    uid: ALICE_UID,
    email: "alice@test.com",
    displayName: "Alice",
    grantedBy: ALICE_UID,
    createdAt: now,
  }));

  // Cannot read or list Bob's profile
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").get());

  // Cannot create own profile with role 'admin' or status 'suspended'
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    email: "alice@test.com",
    displayName: "Alice",
    role: "admin",
    status: "active",
    canEditProfile: true,
    canPostContent: true,
    createdAt: now,
    updatedAt: now,
  }));

  // Can create own standard user profile
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    email: "alice@test.com",
    displayName: "Alice",
    role: "user",
    status: "active",
    canEditProfile: true,
    canPostContent: true,
    createdAt: now,
    updatedAt: now,
  }));

  // Cannot escalate own permissions or inject ghost fields
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({
    canPostContent: false,
    updatedAt: now,
  }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({
    displayName: "Alice 2",
    isVerified: true,
    updatedAt: now,
  }));
});

test("Admin user governance: suspended or edit-restricted user is blocked by backend rules", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@test.com",
      displayName: "Alice",
      role: "user",
      status: "suspended",
      canEditProfile: false,
      canPostContent: false,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@test.com",
    email_verified: true,
  }).firestore();

  // Suspended user cannot edit profile or post records
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({
    displayName: "Hacked Name",
    updatedAt: now,
  }));
  await assertFails(aliceDb.collection("records").doc("rec_1").set({
    title: "Unauthorized",
    content: "Should fail",
    category: "chat",
    status: "published",
    visibility: "all",
    isProtected: false,
    authorUid: ALICE_UID,
    authorName: "Alice",
    createdAt: now,
    updatedAt: now,
  }));
});

test("Protected records and announcements: only Admin can create announcements, send notifications, or delete protected records", async () => {
  const now = new Date();
  const ownerDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@test.com",
    email_verified: true,
  }).firestore();

  // Admin creates announcement, protected record, and notification
  await assertSucceeds(ownerDb.collection("announcements").doc("ann_1").set({
    title: "System Launch",
    body: "Welcome to SEPTIS.",
    priority: "important",
    pinned: true,
    visibility: "all",
    authorUid: ADMIN_UID,
    authorName: "Taha Ali",
    createdAt: now,
    updatedAt: now,
  }));

  await assertSucceeds(ownerDb.collection("records").doc("prot_rec").set({
    title: "Policy Handbook",
    content: "Official policy.",
    category: "resource",
    status: "published",
    visibility: "all",
    isProtected: true,
    authorUid: ALICE_UID,
    authorName: "Alice",
    createdAt: now,
    updatedAt: now,
  }));

  await assertSucceeds(ownerDb.collection("notifications").doc("notif_1").set({
    recipientUid: ALICE_UID,
    title: "Account Verified",
    message: "Your SEPTIS account is active.",
    type: "info",
    isRead: false,
    senderUid: ADMIN_UID,
    createdAt: now,
    updatedAt: now,
  }));

  // Alice cannot create announcements or notifications, and cannot delete protected record
  await assertFails(aliceDb.collection("announcements").doc("ann_fake").set({
    title: "Fake",
    body: "Fake",
    priority: "urgent",
    pinned: true,
    visibility: "all",
    authorUid: ALICE_UID,
    authorName: "Alice",
    createdAt: now,
    updatedAt: now,
  }));
  await assertFails(aliceDb.collection("notifications").doc("notif_fake").set({
    recipientUid: "all",
    title: "Spam",
    message: "Spam",
    type: "alert",
    isRead: false,
    senderUid: ALICE_UID,
    createdAt: now,
    updatedAt: now,
  }));
  await assertFails(aliceDb.collection("records").doc("prot_rec").delete());

  // Alice can read announcements & mark her notification as read
  await assertSucceeds(aliceDb.collection("announcements").where("visibility", "==", "all").get());
  await assertSucceeds(aliceDb.collection("notifications").doc("notif_1").update({
    isRead: true,
    updatedAt: now,
  }));
});

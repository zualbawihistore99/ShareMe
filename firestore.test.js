const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      deviceName: "Pixel",
      avatarColorIndex: 0,
      autoAcceptIncoming: false,
      preferWifiDirect: true,
      transportPreference: "AUTO_BOTH",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(unauthDb.collection("pairingSessions").doc("sess_1").get());
});

test("User profile isolation and schema enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      deviceName: "Alice Pixel",
      avatarColorIndex: 2,
      autoAcceptIncoming: false,
      preferWifiDirect: true,
      transportPreference: "AUTO_BOTH",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cross-user read & write must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      deviceName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Valid partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      deviceName: "Alice Phone",
      transportPreference: "BLUETOOTH_ONLY",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost field & immutable createdAt mutation fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("User transfer history subcollection is owner-only and append-only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("transfers")
      .doc("tr_1")
      .set({
        userId: ALICE_UID,
        fileName: "photo.jpg",
        mimeType: "image/jpeg",
        fileSizeBytes: 204800,
        direction: "SENT",
        status: "COMPLETED",
        peerName: "Bob Galaxy",
        transportMethod: "Wi-Fi Direct",
        averageSpeedBytesPerSec: 10485760,
        createdAt: now,
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transfers").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("transfers").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("transfers")
      .doc("tr_1")
      .update({ fileName: "tampered.jpg" })
  );
});

test("Pairing session lifecycle: create, join, terminal lock, and list query filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("pairingSessions").doc("sess_100").set({
      hostUserId: ALICE_UID,
      hostDeviceName: "Alice Pixel",
      localIp: "192.168.1.42",
      port: 8988,
      sessionPin: "4821",
      status: "WAITING",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Unfiltered list must fail
  await assertFails(bobDb.collection("pairingSessions").get());

  // Host filtered list succeeds
  await assertSucceeds(
    aliceDb
      .collection("pairingSessions")
      .where("hostUserId", "==", ALICE_UID)
      .get()
  );

  // Guest joins session
  await assertSucceeds(
    bobDb.collection("pairingSessions").doc("sess_100").update({
      guestUserId: BOB_UID,
      status: "CONNECTED",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Host closes session
  await assertSucceeds(
    aliceDb.collection("pairingSessions").doc("sess_100").update({
      status: "CLOSED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: updates after CLOSED must fail
  await assertFails(
    aliceDb.collection("pairingSessions").doc("sess_100").update({
      status: "WAITING",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

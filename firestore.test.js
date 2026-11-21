const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_ops_123";
const BOB_UID = "bob_ops_456";

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

function validFindingPayload(uid, runId = "RUN-101") {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    runId,
    file_path: "services/web/package.json",
    line_bounds: "L10-L12",
    threat_category: "TYPOSQUATTING",
    severity: "CRITICAL",
    description: "Typosquatted package lodahs detected in dependencies.",
    replacement_snippet: "\"lodash\": \"^4.17.21\",",
    original_snippet: "\"lodahs\": \"4.17.21\",",
    engine_type: "SECURITY_ENGINE",
    patch_status: "PENDING",
    createdAt: now,
    updatedAt: now,
  };
}

function validRunPayload(uid) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    title: "package.json",
    filePath: "services/web/package.json",
    engineMode: "DUAL_ENGINE",
    modelId: "gemini-4-hydrogen",
    summary: "Detected 1 critical typosquatting dependency.",
    riskScore: 88,
    cyclomaticPeak: 2,
    maxNestingDepth: 1,
    findingCount: 1,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user: denied read and write on all PatchForge paths", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("audit_runs").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("audit_findings")
      .doc("RUN-101-F1")
      .set(validFindingPayload(ALICE_UID))
  );
});

test("Owner can create/update profile, audit_runs, and audit_findings; rejects shadow fields & future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Create user profile
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Operator",
      email: "alice@example.com",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Create audit run
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("audit_runs")
      .doc("RUN-101")
      .set(validRunPayload(ALICE_UID))
  );

  // List own audit_runs
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("audit_runs").get()
  );

  // Create AuditFinding matching Kotlin payload (NO @DocumentId id key)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("audit_findings")
      .doc("RUN-101-F1")
      .set(validFindingPayload(ALICE_UID))
  );

  // Partial update patch_status succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("audit_findings")
      .doc("RUN-101-F1")
      .update({
        patch_status: "APPROVED",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Reject mutating immutable createdAt or file_path on finding update
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("audit_findings")
      .doc("RUN-101-F1")
      .update({
        file_path: "tampered.json",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Reject shadow/ghost field or @DocumentId 'id' on create
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("audit_findings")
      .doc("RUN-101-F2")
      .set({
        ...validFindingPayload(ALICE_UID),
        id: "RUN-101-F2",
      })
  );

  // Reject future timestamp spoofing
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("audit_findings")
      .doc("RUN-101-F3")
      .set({
        ...validFindingPayload(ALICE_UID),
        createdAt: new Date(Date.now() + 60000),
      })
  );
});

test("Cross-user isolation: Bob cannot read, list, or mutate Alice's runs or findings", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("audit_findings")
      .doc("RUN-101-F1")
      .set(validFindingPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("audit_findings")
      .doc("RUN-101-F1")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("audit_findings").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("audit_findings")
      .doc("RUN-101-F1")
      .update({
        patch_status: "DISMISSED",
        updatedAt: new Date(Date.now() - 500),
      })
  );
});

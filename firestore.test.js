import { test, describe, before, after, beforeEach } from "node:test";
import fs from "node:fs";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("/usr/local/lib/node_modules/@firebase/rules-unit-testing");
const {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  serverTimestamp,
  Timestamp,
} = require("/usr/local/lib/node_modules/@firebase/rules-unit-testing/node_modules/firebase/firestore");

const PROJECT_ID = "babbal-finance-rules-test";
const DATABASE_ID = "ai-studio-android-babbalfi-3f72b897-3dd7-4406-892f-dbf2e79c64d6";

let testEnv;

before(async () => {
  const hostEnv = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085";
  const [host, portStr] = hostEnv.split(":");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host,
      port: Number(portStr),
      rules: fs.readFileSync("firestore.rules", "utf8"),
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

function dbFor(uid) {
  if (!uid) {
    return testEnv.unauthenticatedContext().firestore({ databaseId: DATABASE_ID });
  }
  return testEnv.authenticatedContext(uid, { email: `${uid}@example.com` }).firestore({ databaseId: DATABASE_ID });
}

function validUserProfile(uid) {
  return {
    uid,
    email: `${uid}@example.com`,
    displayName: "Test User",
    personalWorkspaceName: "Personal",
    businessWorkspaceName: "GVVS Business",
    currencySymbol: "₹",
    currencyCode: "INR",
    dateFormatId: "DD_MM_YYYY",
    financialYearStartMonth: 4,
    themeModeId: "LIGHT",
    lastSyncedDevice: "Pixel-Test",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validTransaction() {
  return {
    workspaceId: "PERSONAL",
    typeId: "EXPENSE",
    amountPaise: 25000,
    dateMillis: 1730000000000,
    categorySyncId: "default_PERSONAL_EXPENSE_food",
    accountSyncId: "default_PERSONAL_CASH",
    toAccountSyncId: "",
    description: "Lunch",
    createdAtMillis: 1730000000000,
    updatedAtMillis: 1730000000000,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

describe("BABBAL Finance Firestore Security Rules", () => {
  test("unauthenticated user cannot read or write any user profile or transactions", async () => {
    const anonDb = dbFor(null);
    await assertFails(getDoc(doc(anonDb, "users", "alice")));
    await assertFails(setDoc(doc(anonDb, "users", "alice"), validUserProfile("alice")));
    await assertFails(setDoc(doc(anonDb, "users", "alice", "transactions", "tx1"), validTransaction()));
  });

  test("owner can create, read, and update their profile while keeping uid and createdAt immutable", async () => {
    const aliceDb = dbFor("alice");
    const profileRef = doc(aliceDb, "users", "alice");

    await assertSucceeds(setDoc(profileRef, validUserProfile("alice")));
    await assertSucceeds(getDoc(profileRef));

    await assertSucceeds(
      updateDoc(profileRef, {
        businessWorkspaceName: "GVVS HQ",
        lastSyncedDevice: "Chrome-PC",
        updatedAt: serverTimestamp(),
      })
    );

    await assertFails(
      updateDoc(profileRef, {
        uid: "bob",
        updatedAt: serverTimestamp(),
      })
    );
    await assertFails(
      updateDoc(profileRef, {
        createdAt: Timestamp.fromMillis(1000),
        updatedAt: serverTimestamp(),
      })
    );
    await assertFails(deleteDoc(profileRef));
  });

  test("cross-user reads, writes, updates, and deletes are strictly denied", async () => {
    const aliceDb = dbFor("alice");
    const bobDb = dbFor("bob");

    await assertSucceeds(setDoc(doc(aliceDb, "users", "alice"), validUserProfile("alice")));
    await assertSucceeds(setDoc(doc(aliceDb, "users", "alice", "transactions", "tx_1"), validTransaction()));

    await assertFails(getDoc(doc(bobDb, "users", "alice")));
    await assertFails(getDoc(doc(bobDb, "users", "alice", "transactions", "tx_1")));
    await assertFails(setDoc(doc(bobDb, "users", "alice", "transactions", "tx_2"), validTransaction()));
    await assertFails(
      updateDoc(doc(bobDb, "users", "alice", "transactions", "tx_1"), {
        description: "Tampered",
        updatedAt: serverTimestamp(),
      })
    );
    await assertFails(deleteDoc(doc(bobDb, "users", "alice", "transactions", "tx_1")));
  });

  test("validates schema, types, field allowlists, and bounds on financial collections", async () => {
    const aliceDb = dbFor("alice");

    const accRef = doc(aliceDb, "users", "alice", "accounts", "acc_1");
    await assertSucceeds(
      setDoc(accRef, {
        name: "HDFC Bank",
        workspaceId: "GVVS",
        typeId: "BANK",
        openingBalancePaise: 500000,
        isDefault: false,
        createdAtMillis: 1730000000000,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
    );

    await assertFails(
      setDoc(doc(aliceDb, "users", "alice", "accounts", "acc_bad"), {
        name: "Invalid",
        workspaceId: "UNKNOWN",
        typeId: "BANK",
        openingBalancePaise: 0,
        isDefault: false,
        createdAtMillis: 1730000000000,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
    );

    const txRef = doc(aliceDb, "users", "alice", "transactions", "tx_1");
    await assertSucceeds(setDoc(txRef, validTransaction()));
    await assertSucceeds(
      updateDoc(txRef, {
        amountPaise: 32000,
        description: "Team lunch",
        updatedAtMillis: 1730000005000,
        updatedAt: serverTimestamp(),
      })
    );
    await assertSucceeds(deleteDoc(txRef));

    await assertFails(
      setDoc(doc(aliceDb, "users", "alice", "transactions", "tx_zero"), {
        ...validTransaction(),
        amountPaise: 0,
      })
    );

    await assertFails(
      setDoc(doc(aliceDb, "users", "alice", "transactions", "tx_extra"), {
        ...validTransaction(),
        injectedAdminFlag: true,
      })
    );

    await assertFails(
      setDoc(doc(aliceDb, "users", "alice", "transactions", "tx_long"), {
        ...validTransaction(),
        description: "x".repeat(501),
      })
    );

    await assertSucceeds(
      setDoc(doc(aliceDb, "users", "alice", "budgets", "b_1"), {
        workspaceId: "PERSONAL",
        categorySyncId: "",
        month: 11,
        year: 2026,
        limitPaise: 2500000,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
    );

    await assertSucceeds(
      setDoc(doc(aliceDb, "users", "alice", "savings_goals", "g_1"), {
        workspaceId: "PERSONAL",
        name: "Emergency Fund",
        isEmergencyFund: true,
        targetPaise: 10000000,
        currentSavedPaise: 2500000,
        monthlyTargetPaise: 500000,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
    );

    await assertSucceeds(
      setDoc(doc(aliceDb, "users", "alice", "loans", "l_1"), {
        workspaceId: "GVVS",
        typeId: "LENT",
        personName: "Ramesh",
        principalPaise: 500000,
        repaidPaise: 0,
        accountSyncId: "default_GVVS_BANK",
        dateMillis: 1730000000000,
        expectedRepaymentDateMillis: 0,
        notes: "Vendor advance",
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
    );

    await assertSucceeds(
      setDoc(doc(aliceDb, "users", "alice", "loan_repayments", "r_1"), {
        loanSyncId: "l_1",
        amountPaise: 200000,
        dateMillis: 1730100000000,
        accountSyncId: "default_GVVS_BANK",
        notes: "First installment",
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
    );
  });

  test("rejects arbitrary collection paths outside defined schema", async () => {
    const aliceDb = dbFor("alice");
    await assertFails(setDoc(doc(aliceDb, "users", "alice", "unknown_col", "d1"), { foo: "bar" }));
    await assertFails(setDoc(doc(aliceDb, "public_notes", "n1"), { text: "hello" }));
  });
});

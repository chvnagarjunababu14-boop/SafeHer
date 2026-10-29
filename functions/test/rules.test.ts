import * as fs from "fs";
import * as path from "path";
import { fileURLToPath } from "url";
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from "@firebase/rules-unit-testing";
import {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  getDocs,
  query,
  where,
  serverTimestamp,
  Timestamp,
  writeBatch,
} from "firebase/firestore";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

describe("SafeHer Cloud Firestore Security Rules Test Suite", () => {
  let testEnv: any;
  const rulesPath = path.resolve(__dirname, "../../firestore.rules");
  const rules = fs.readFileSync(rulesPath, "utf8");

  const futureExpiry = () =>
    Timestamp.fromMillis(Date.now() + 47 * 60 * 60 * 1000);

  const pendingInvite = (inviteId: string, senderUid: string, code = "BLUE-TIGER-42-MAPLE") => ({
    id: inviteId,
    code,
    senderUid,
    senderDisplayName: "SafeHer User",
    senderEmail: "user@example.com",
    status: "PENDING",
    createdAt: serverTimestamp(),
    expiresAt: futureExpiry(),
  });

  before(async function () {
    this.timeout(60000);
    testEnv = await initializeTestEnvironment({
      projectId: "safeher-rules-test",
      firestore: {
        rules,
        host: "127.0.0.1",
        port: 8080,
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

  describe("Default Catch-All Rule", () => {
    it("denies unauthenticated read/write to unknown collections", async () => {
      const unauthedDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(getDoc(doc(unauthedDb, "random_collection", "doc1")));
      await assertFails(setDoc(doc(unauthedDb, "random_collection", "doc1"), { test: 1 }));
    });

    it("denies authenticated read/write to unknown collections", async () => {
      const authedDb = testEnv.authenticatedContext("user1").firestore();
      await assertFails(getDoc(doc(authedDb, "random_collection", "doc1")));
      await assertFails(setDoc(doc(authedDb, "random_collection", "doc1"), { test: 1 }));
    });
  });

  describe("/users/{uid} User Profiles & FCM Tokens", () => {
    it("allows user to create their own profile/FCM tokens", async () => {
      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertSucceeds(
        setDoc(doc(user1Db, "users", "user1"), {
          fcmTokens: {
            token_xyz: { deviceModel: "Pixel 8", updatedAt: Date.now() },
          },
        })
      );
    });

    it("denies user from creating another user's document", async () => {
      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertFails(
        setDoc(doc(user1Db, "users", "user2"), {
          fcmTokens: { token_abc: { deviceModel: "Galaxy S23" } },
        })
      );
    });

    it("denies unauthenticated writes to /users/{uid}", async () => {
      const unauthedDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(
        setDoc(doc(unauthedDb, "users", "user1"), {
          fcmTokens: { token_xyz: { deviceModel: "Pixel 8" } },
        })
      );
    });

    it("denies user from self-assigning privileged claims on create", async () => {
      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertFails(
        setDoc(doc(user1Db, "users", "user1"), {
          role: "admin",
          fcmTokens: {},
        })
      );
    });

    it("allows user to update their own profile and FCM tokens", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "users", "user1"), {
          fcmTokens: { token_1: { deviceModel: "Pixel 7" } },
        });
      });

      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertSucceeds(
        updateDoc(doc(user1Db, "users", "user1"), {
          "fcmTokens.token_2": { deviceModel: "Pixel 8" },
        })
      );
    });

    it("denies user from modifying privileged fields on update", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "users", "user1"), {
          fcmTokens: {},
        });
      });

      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertFails(
        updateDoc(doc(user1Db, "users", "user1"), {
          role: "admin",
        })
      );
    });

    it("allows user to read their own profile", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "users", "user1"), {
          fcmTokens: { secret_token: {} },
        });
      });

      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertSucceeds(getDoc(doc(user1Db, "users", "user1")));
    });

    it("denies user from reading another user's profile/tokens", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "users", "user2"), {
          fcmTokens: { private_token: {} },
        });
      });

      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertFails(getDoc(doc(user1Db, "users", "user2")));
    });

    it("denies client deletion of user profiles", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "users", "user1"), { active: true });
      });

      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertFails(deleteDoc(doc(user1Db, "users", "user1")));
    });
  });

  describe("/invitations/{inviteId} Firestore-only phrase invites", () => {
    it("denies unauthenticated create and get", async () => {
      const unauthedDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(setDoc(doc(unauthedDb, "invitations", "inv_a"), pendingInvite("inv_a", "user1")));
      await assertFails(getDoc(doc(unauthedDb, "invitations", "inv_a")));
    });

    it("allows authenticated user to create a valid invitation for own UID", async () => {
      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertSucceeds(
        setDoc(doc(user1Db, "invitations", "inv_user1"), pendingInvite("inv_user1", "user1"))
      );
    });

    it("denies creating an invitation impersonating another UID", async () => {
      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertFails(
        setDoc(doc(user1Db, "invitations", "inv_forge"), pendingInvite("inv_forge", "user2"))
      );
    });

    it("denies malformed invitations", async () => {
      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertFails(
        setDoc(doc(user1Db, "invitations", "inv_bad"), {
          id: "inv_bad",
          code: "SHORT",
          senderUid: "user1",
          senderDisplayName: "User",
          senderEmail: "",
          status: "PENDING",
          createdAt: serverTimestamp(),
          expiresAt: futureExpiry(),
        })
      );
      await assertFails(
        setDoc(doc(user1Db, "invitations", "inv_accepted"), {
          ...pendingInvite("inv_accepted", "user1"),
          status: "ACCEPTED",
        })
      );
    });

    it("denies overwriting an existing invitation via create semantics", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "invitations", "inv_existing"), {
          id: "inv_existing",
          code: "BLUE-TIGER-42-MAPLE",
          senderUid: "user1",
          senderDisplayName: "User",
          senderEmail: "",
          status: "PENDING",
          createdAt: Timestamp.now(),
          expiresAt: futureExpiry(),
        });
      });

      const user1Db = testEnv.authenticatedContext("user1").firestore();
      // set on existing doc is an update; claim/revoke shapes do not match a full rewrite
      await assertFails(
        setDoc(doc(user1Db, "invitations", "inv_existing"), pendingInvite("inv_existing", "user1", "OAK-RIVER-19-PINE"))
      );
    });

    it("allows get of missing invitation for authenticated collision checks", async () => {
      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertSucceeds(getDoc(doc(user1Db, "invitations", "missing_hash")));
    });

    it("allows owner to list own pending invitations and denies unrelated list", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        const db = context.firestore();
        await setDoc(doc(db, "invitations", "inv_a"), {
          id: "inv_a",
          code: "BLUE-TIGER-42-MAPLE",
          senderUid: "user1",
          senderDisplayName: "User",
          senderEmail: "",
          status: "PENDING",
          createdAt: Timestamp.now(),
          expiresAt: futureExpiry(),
        });
        await setDoc(doc(db, "invitations", "inv_b"), {
          id: "inv_b",
          code: "OAK-RIVER-19-PINE",
          senderUid: "user2",
          senderDisplayName: "Other",
          senderEmail: "",
          status: "PENDING",
          createdAt: Timestamp.now(),
          expiresAt: futureExpiry(),
        });
      });

      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertSucceeds(
        getDocs(query(collection(user1Db, "invitations"), where("senderUid", "==", "user1")))
      );
      await assertFails(
        getDocs(query(collection(user1Db, "invitations"), where("senderUid", "==", "user2")))
      );
    });

    it("allows eligible guardian to claim pending invite and create relationship atomically", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "invitations", "inv_claim"), {
          id: "inv_claim",
          code: "BLUE-TIGER-42-MAPLE",
          senderUid: "user1",
          senderDisplayName: "SafeHer User",
          senderEmail: "user@example.com",
          status: "PENDING",
          createdAt: Timestamp.now(),
          expiresAt: futureExpiry(),
        });
      });

      const guardianDb = testEnv.authenticatedContext("guardian2").firestore();
      const inviteRef = doc(guardianDb, "invitations", "inv_claim");
      const relRef = doc(guardianDb, "relationships", "user1_guardian2");
      const batch = writeBatch(guardianDb);
      batch.update(inviteRef, {
        status: "ACCEPTED",
        acceptedByUid: "guardian2",
        acceptedAt: serverTimestamp(),
      });
      batch.set(relRef, {
        id: "user1_guardian2",
        senderUid: "user1",
        senderDisplayName: "SafeHer User",
        senderEmail: "user@example.com",
        guardianUid: "guardian2",
        guardianDisplayName: "Guardian",
        guardianEmail: "g@example.com",
        status: "ACTIVE",
        createdAt: serverTimestamp(),
        sourceInviteId: "inv_claim",
      });
      await assertSucceeds(batch.commit());
    });

    it("denies self-connection claim", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "invitations", "inv_self"), {
          id: "inv_self",
          code: "BLUE-TIGER-42-MAPLE",
          senderUid: "user1",
          senderDisplayName: "User",
          senderEmail: "",
          status: "PENDING",
          createdAt: Timestamp.now(),
          expiresAt: futureExpiry(),
        });
      });

      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertFails(
        updateDoc(doc(user1Db, "invitations", "inv_self"), {
          status: "ACCEPTED",
          acceptedByUid: "user1",
          acceptedAt: serverTimestamp(),
        })
      );
    });

    it("denies claiming expired invitations", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "invitations", "inv_exp"), {
          id: "inv_exp",
          code: "BLUE-TIGER-42-MAPLE",
          senderUid: "user1",
          senderDisplayName: "User",
          senderEmail: "",
          status: "PENDING",
          createdAt: Timestamp.now(),
          expiresAt: Timestamp.fromMillis(Date.now() - 60_000),
        });
      });

      const guardianDb = testEnv.authenticatedContext("guardian2").firestore();
      await assertFails(
        updateDoc(doc(guardianDb, "invitations", "inv_exp"), {
          status: "ACCEPTED",
          acceptedByUid: "guardian2",
          acceptedAt: serverTimestamp(),
        })
      );
    });

    it("denies claiming revoked or already accepted invitations", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        const db = context.firestore();
        await setDoc(doc(db, "invitations", "inv_rev"), {
          id: "inv_rev",
          code: "BLUE-TIGER-42-MAPLE",
          senderUid: "user1",
          senderDisplayName: "User",
          senderEmail: "",
          status: "REVOKED",
          createdAt: Timestamp.now(),
          expiresAt: futureExpiry(),
        });
        await setDoc(doc(db, "invitations", "inv_acc"), {
          id: "inv_acc",
          code: "OAK-RIVER-19-PINE",
          senderUid: "user1",
          senderDisplayName: "User",
          senderEmail: "",
          status: "ACCEPTED",
          acceptedByUid: "guardian2",
          createdAt: Timestamp.now(),
          expiresAt: futureExpiry(),
        });
      });

      const guardianDb = testEnv.authenticatedContext("guardian3").firestore();
      await assertFails(
        updateDoc(doc(guardianDb, "invitations", "inv_rev"), {
          status: "ACCEPTED",
          acceptedByUid: "guardian3",
          acceptedAt: serverTimestamp(),
        })
      );
      await assertFails(
        updateDoc(doc(guardianDb, "invitations", "inv_acc"), {
          status: "ACCEPTED",
          acceptedByUid: "guardian3",
          acceptedAt: serverTimestamp(),
        })
      );
    });

    it("allows owner to revoke unused pending invitation", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "invitations", "inv_own"), {
          id: "inv_own",
          code: "BLUE-TIGER-42-MAPLE",
          senderUid: "user1",
          senderDisplayName: "User",
          senderEmail: "",
          status: "PENDING",
          createdAt: Timestamp.now(),
          expiresAt: futureExpiry(),
        });
      });

      const user1Db = testEnv.authenticatedContext("user1").firestore();
      await assertSucceeds(
        updateDoc(doc(user1Db, "invitations", "inv_own"), {
          status: "REVOKED",
          revokedAt: serverTimestamp(),
          revokedBy: "user1",
        })
      );
    });

    it("denies claimant impersonation on acceptedByUid", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "invitations", "inv_imp"), {
          id: "inv_imp",
          code: "BLUE-TIGER-42-MAPLE",
          senderUid: "user1",
          senderDisplayName: "User",
          senderEmail: "",
          status: "PENDING",
          createdAt: Timestamp.now(),
          expiresAt: futureExpiry(),
        });
      });

      const guardianDb = testEnv.authenticatedContext("guardian2").firestore();
      await assertFails(
        updateDoc(doc(guardianDb, "invitations", "inv_imp"), {
          status: "ACCEPTED",
          acceptedByUid: "guardian9",
          acceptedAt: serverTimestamp(),
        })
      );
    });

    it("denies non-owner from revoking a pending invitation", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "invitations", "inv_nr"), {
          id: "inv_nr",
          code: "BLUE-TIGER-42-MAPLE",
          senderUid: "user1",
          senderDisplayName: "User",
          senderEmail: "",
          status: "PENDING",
          createdAt: Timestamp.now(),
          expiresAt: futureExpiry(),
        });
      });

      const strangerDb = testEnv.authenticatedContext("guardian2").firestore();
      await assertFails(
        updateDoc(doc(strangerDb, "invitations", "inv_nr"), {
          status: "REVOKED",
          revokedAt: serverTimestamp(),
          revokedBy: "guardian2",
        })
      );
    });

    it("denies claimant from altering sender identity fields during claim", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "invitations", "inv_mut"), {
          id: "inv_mut",
          code: "BLUE-TIGER-42-MAPLE",
          senderUid: "user1",
          senderDisplayName: "Real User",
          senderEmail: "real@example.com",
          status: "PENDING",
          createdAt: Timestamp.now(),
          expiresAt: futureExpiry(),
        });
      });

      const guardianDb = testEnv.authenticatedContext("guardian2").firestore();
      await assertFails(
        updateDoc(doc(guardianDb, "invitations", "inv_mut"), {
          status: "ACCEPTED",
          acceptedByUid: "guardian2",
          acceptedAt: serverTimestamp(),
          senderDisplayName: "Spoofed Name",
          senderEmail: "attacker@evil.test",
        })
      );
    });

    it("denies second concurrent-style claim after invitation is accepted", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "invitations", "inv_race"), {
          id: "inv_race",
          code: "BLUE-TIGER-42-MAPLE",
          senderUid: "user1",
          senderDisplayName: "User",
          senderEmail: "",
          status: "PENDING",
          createdAt: Timestamp.now(),
          expiresAt: futureExpiry(),
        });
      });

      const firstDb = testEnv.authenticatedContext("guardian2").firestore();
      const inviteRef = doc(firstDb, "invitations", "inv_race");
      const relRef = doc(firstDb, "relationships", "user1_guardian2");
      const batch = writeBatch(firstDb);
      batch.update(inviteRef, {
        status: "ACCEPTED",
        acceptedByUid: "guardian2",
        acceptedAt: serverTimestamp(),
      });
      batch.set(relRef, {
        id: "user1_guardian2",
        senderUid: "user1",
        senderDisplayName: "User",
        senderEmail: "",
        guardianUid: "guardian2",
        guardianDisplayName: "Guardian Two",
        guardianEmail: "",
        status: "ACTIVE",
        createdAt: serverTimestamp(),
        sourceInviteId: "inv_race",
      });
      await assertSucceeds(batch.commit());

      const secondDb = testEnv.authenticatedContext("guardian3").firestore();
      const secondBatch = writeBatch(secondDb);
      secondBatch.update(doc(secondDb, "invitations", "inv_race"), {
        status: "ACCEPTED",
        acceptedByUid: "guardian3",
        acceptedAt: serverTimestamp(),
      });
      secondBatch.set(doc(secondDb, "relationships", "user1_guardian3"), {
        id: "user1_guardian3",
        senderUid: "user1",
        senderDisplayName: "User",
        senderEmail: "",
        guardianUid: "guardian3",
        guardianDisplayName: "Guardian Three",
        guardianEmail: "",
        status: "ACTIVE",
        createdAt: serverTimestamp(),
        sourceInviteId: "inv_race",
      });
      await assertFails(secondBatch.commit());
    });

    it("denies unrelated users from reading accepted invitations", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "invitations", "inv_acc_priv"), {
          id: "inv_acc_priv",
          code: "BLUE-TIGER-42-MAPLE",
          senderUid: "user1",
          senderDisplayName: "User",
          senderEmail: "private@example.com",
          status: "ACCEPTED",
          acceptedByUid: "guardian2",
          createdAt: Timestamp.now(),
          expiresAt: futureExpiry(),
        });
      });

      const strangerDb = testEnv.authenticatedContext("intruder").firestore();
      const priorClaimantDb = testEnv.authenticatedContext("guardian2").firestore();
      await assertFails(getDoc(doc(strangerDb, "invitations", "inv_acc_priv")));
      await assertFails(getDoc(doc(priorClaimantDb, "invitations", "inv_acc_priv")));
      const ownerDb = testEnv.authenticatedContext("user1").firestore();
      await assertSucceeds(getDoc(doc(ownerDb, "invitations", "inv_acc_priv")));
    });

    it("denies unauthenticated list of invitations", async () => {
      const unauthedDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(getDocs(collection(unauthedDb, "invitations")));
    });
  });

  describe("/relationships/{relationshipId}", () => {
    beforeEach(async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "relationships", "rel_1_2"), {
          id: "rel_1_2",
          senderUid: "user1",
          guardianUid: "guardian2",
          status: "ACTIVE",
          sourceInviteId: "inv_seed",
        });
      });
    });

    it("allows protected sender and guardian to read relationship", async () => {
      const senderDb = testEnv.authenticatedContext("user1").firestore();
      const guardianDb = testEnv.authenticatedContext("guardian2").firestore();
      await assertSucceeds(getDoc(doc(senderDb, "relationships", "rel_1_2")));
      await assertSucceeds(getDoc(doc(guardianDb, "relationships", "rel_1_2")));
    });

    it("denies third-party users from reading relationship", async () => {
      const intruderDb = testEnv.authenticatedContext("intruder3").firestore();
      await assertFails(getDoc(doc(intruderDb, "relationships", "rel_1_2")));
    });

    it("denies forging a relationship without a valid accepted invitation", async () => {
      const guardianDb = testEnv.authenticatedContext("guardian2").firestore();
      await assertFails(
        setDoc(doc(guardianDb, "relationships", "user9_guardian2"), {
          id: "user9_guardian2",
          senderUid: "user9",
          senderDisplayName: "Fake",
          senderEmail: "",
          guardianUid: "guardian2",
          guardianDisplayName: "Guardian",
          guardianEmail: "",
          status: "ACTIVE",
          createdAt: serverTimestamp(),
          sourceInviteId: "missing_invite",
        })
      );
    });

    it("allows participants to revoke an active relationship", async () => {
      const senderDb = testEnv.authenticatedContext("user1").firestore();
      await assertSucceeds(
        updateDoc(doc(senderDb, "relationships", "rel_1_2"), {
          status: "REVOKED",
          revokedAt: serverTimestamp(),
          revokedBy: "user1",
        })
      );
    });

    it("denies unauthorized clients from modifying participant UIDs", async () => {
      const senderDb = testEnv.authenticatedContext("user1").firestore();
      await assertFails(
        updateDoc(doc(senderDb, "relationships", "rel_1_2"), {
          status: "REVOKED",
          guardianUid: "hacker",
          revokedAt: serverTimestamp(),
          revokedBy: "user1",
        })
      );
    });

    it("denies relationship create that reuses another guardian's accepted invite", async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), "invitations", "inv_other"), {
          id: "inv_other",
          code: "OAK-RIVER-19-PINE",
          senderUid: "user1",
          senderDisplayName: "User",
          senderEmail: "",
          status: "ACCEPTED",
          acceptedByUid: "guardian2",
          createdAt: Timestamp.now(),
          expiresAt: futureExpiry(),
        });
      });

      const thiefDb = testEnv.authenticatedContext("guardian3").firestore();
      await assertFails(
        setDoc(doc(thiefDb, "relationships", "user1_guardian3"), {
          id: "user1_guardian3",
          senderUid: "user1",
          senderDisplayName: "User",
          senderEmail: "",
          guardianUid: "guardian3",
          guardianDisplayName: "Thief",
          guardianEmail: "",
          status: "ACTIVE",
          createdAt: serverTimestamp(),
          sourceInviteId: "inv_other",
        })
      );
    });

    it("denies reading a relationship after revocation", async () => {
      const senderDb = testEnv.authenticatedContext("user1").firestore();
      const guardianDb = testEnv.authenticatedContext("guardian2").firestore();
      await assertSucceeds(
        updateDoc(doc(senderDb, "relationships", "rel_1_2"), {
          status: "REVOKED",
          revokedAt: serverTimestamp(),
          revokedBy: "user1",
        })
      );
      await assertFails(getDoc(doc(senderDb, "relationships", "rel_1_2")));
      await assertFails(getDoc(doc(guardianDb, "relationships", "rel_1_2")));
    });

    it("denies unauthenticated relationship reads and writes", async () => {
      const unauthedDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(getDoc(doc(unauthedDb, "relationships", "rel_1_2")));
      await assertFails(
        setDoc(doc(unauthedDb, "relationships", "forged"), {
          id: "forged",
          senderUid: "user1",
          guardianUid: "guardian2",
          status: "ACTIVE",
        })
      );
    });
  });

  describe("Default-deny for journeys and unknown remote collections", () => {
    it("denies authenticated reads and writes to journeys and locations collections", async () => {
      const guardianDb = testEnv.authenticatedContext("guardian2").firestore();
      await assertFails(getDoc(doc(guardianDb, "journeys", "trip1")));
      await assertFails(setDoc(doc(guardianDb, "journeys", "trip1"), { ownerUid: "user1" }));
      await assertFails(getDoc(doc(guardianDb, "locations", "user1")));
      await assertFails(setDoc(doc(guardianDb, "locations", "user1"), { lat: 1, lng: 2 }));
    });
  });

  describe("/alerts/{alertId} and subcollections", () => {
    beforeEach(async () => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        const db = context.firestore();
        await setDoc(doc(db, "alerts", "alert1"), {
          senderUid: "user1",
          recipientUids: ["guardian2", "guardian3"],
          status: "ACTIVE",
        });
        await setDoc(doc(db, "alerts", "alert1", "sensitive", "location"), {
          latitude: 12.9716,
          longitude: 77.5946,
        });
        await setDoc(doc(db, "alerts", "alert1", "delivery_status", "guardian2"), {
          guardianUid: "guardian2",
          status: "QUEUED",
        });
      });
    });

    it("allows sender and authorized guardian to read alert", async () => {
      const senderDb = testEnv.authenticatedContext("user1").firestore();
      const guardianDb = testEnv.authenticatedContext("guardian2").firestore();
      await assertSucceeds(getDoc(doc(senderDb, "alerts", "alert1")));
      await assertSucceeds(getDoc(doc(guardianDb, "alerts", "alert1")));
    });

    it("denies unauthorized user from reading alert and sensitive location", async () => {
      const intruderDb = testEnv.authenticatedContext("intruder").firestore();
      await assertFails(getDoc(doc(intruderDb, "alerts", "alert1")));
      await assertFails(getDoc(doc(intruderDb, "alerts", "alert1", "sensitive", "location")));
    });

    it("denies direct client writes to alerts", async () => {
      const senderDb = testEnv.authenticatedContext("user1").firestore();
      await assertFails(
        setDoc(doc(senderDb, "alerts", "fake_alert"), { senderUid: "user1" })
      );
    });

    it("allows authorized recipient and sender to read sensitive location", async () => {
      const senderDb = testEnv.authenticatedContext("user1").firestore();
      const guardianDb = testEnv.authenticatedContext("guardian2").firestore();
      await assertSucceeds(getDoc(doc(senderDb, "alerts", "alert1", "sensitive", "location")));
      await assertSucceeds(getDoc(doc(guardianDb, "alerts", "alert1", "sensitive", "location")));
    });

    it("denies direct client writes to sensitive location and delivery status", async () => {
      const senderDb = testEnv.authenticatedContext("user1").firestore();
      const guardian2Db = testEnv.authenticatedContext("guardian2").firestore();
      await assertFails(
        setDoc(doc(senderDb, "alerts", "alert1", "sensitive", "location"), { latitude: 0, longitude: 0 })
      );
      await assertFails(
        setDoc(doc(guardian2Db, "alerts", "alert1", "delivery_status", "guardian2"), {
          status: "ACKNOWLEDGED",
        })
      );
    });
  });
});

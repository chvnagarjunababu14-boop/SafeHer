import * as crypto from "crypto";
import { getFirestore, Timestamp, FieldValue } from "firebase-admin/firestore";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import {
  CreateInviteResponse,
  AcceptInviteRequest,
  AcceptInviteResponse,
  RevokeRelationshipRequest,
  GuardianInvitation,
  GuardianRelationship,
} from "./types";

function generateSecureCode(): string {
  // 8 uppercase alphanumeric characters, excluding confusing characters (0, O, I, 1, L)
  const chars = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
  const bytes = crypto.randomBytes(8);
  let result = "";
  for (let i = 0; i < 8; i++) {
    result += chars[bytes[i] % chars.length];
  }
  return result;
}

export async function handleCreateGuardianInvite(
  request: CallableRequest
): Promise<CreateInviteResponse> {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required to create guardian invite.");
  }

  const db = getFirestore();
  const callerUid = request.auth.uid;
  const callerEmail = request.auth.token.email || "";
  const callerName = request.auth.token.name || "SafeHer User";

  // Rate-limiting: count active pending invites for this caller (max 5)
  const pendingSnapshot = await db
    .collection("invitations")
    .where("senderUid", "==", callerUid)
    .where("status", "==", "PENDING")
    .get();

  const now = Timestamp.now();
  const activePending = pendingSnapshot.docs.filter(
    (doc) => doc.data().expiresAt.toMillis() > now.toMillis()
  );

  if (activePending.length >= 5) {
    throw new HttpsError(
      "resource-exhausted",
      "You have reached the maximum of 5 active pending invites. Please wait for them to be accepted, expire, or cancel them."
    );
  }

  const code = generateSecureCode();
  const inviteRef = db.collection("invitations").doc();
  const expiresAt = Timestamp.fromMillis(Date.now() + 48 * 60 * 60 * 1000); // 48 hours

  const inviteData: GuardianInvitation = {
    id: inviteRef.id,
    code,
    senderUid: callerUid,
    senderEmail: callerEmail,
    senderDisplayName: callerName,
    status: "PENDING",
    createdAt: now,
    expiresAt,
  };

  await inviteRef.set(inviteData);

  return {
    inviteId: inviteRef.id,
    code,
    expiresAtMillis: expiresAt.toMillis(),
  };
}

export async function handleAcceptGuardianInvite(
  request: CallableRequest<AcceptInviteRequest>
): Promise<AcceptInviteResponse> {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required to accept invite.");
  }

  const callerUid = request.auth.uid;
  const callerEmail = request.auth.token.email || "";
  const callerName = request.auth.token.name || "SafeHer Guardian";

  const rawCode = request.data?.code;
  if (!rawCode || typeof rawCode !== "string") {
    throw new HttpsError("invalid-argument", "Valid 8-character invitation code is required.");
  }

  const normalizedCode = rawCode.trim().toUpperCase();
  if (normalizedCode.length < 6 || normalizedCode.length > 12) {
    throw new HttpsError("invalid-argument", "Invalid invitation code format.");
  }

  const db = getFirestore();
  const now = Timestamp.now();

  const querySnapshot = await db
    .collection("invitations")
    .where("code", "==", normalizedCode)
    .limit(1)
    .get();

  if (querySnapshot.empty) {
    throw new HttpsError("not-found", "Invitation code not found or invalid.");
  }

  const inviteDoc = querySnapshot.docs[0];
  const inviteData = inviteDoc.data() as GuardianInvitation;

  if (inviteData.status !== "PENDING") {
    throw new HttpsError("failed-precondition", "This invitation code has already been used or revoked.");
  }

  if (inviteData.expiresAt.toMillis() <= now.toMillis()) {
    throw new HttpsError("deadline-exceeded", "This invitation code has expired.");
  }

  if (inviteData.senderUid === callerUid) {
    throw new HttpsError("invalid-argument", "You cannot accept your own guardian invitation.");
  }

  const relationshipId = `${inviteData.senderUid}_${callerUid}`;
  const relationshipRef = db.collection("relationships").doc(relationshipId);

  await db.runTransaction(async (transaction) => {
    const existingRelDoc = await transaction.get(relationshipRef);
    if (existingRelDoc.exists && existingRelDoc.data()?.status === "ACTIVE") {
      throw new HttpsError(
        "already-exists",
        "An active guardian relationship already exists with this user."
      );
    }

    // Mark invite accepted
    transaction.update(inviteDoc.ref, {
      status: "ACCEPTED",
      acceptedByUid: callerUid,
      acceptedAt: FieldValue.serverTimestamp(),
    });

    const relationshipData: GuardianRelationship = {
      id: relationshipId,
      senderUid: inviteData.senderUid,
      senderDisplayName: inviteData.senderDisplayName,
      senderEmail: inviteData.senderEmail,
      guardianUid: callerUid,
      guardianDisplayName: callerName,
      guardianEmail: callerEmail,
      status: "ACTIVE",
      createdAt: now,
    };

    transaction.set(relationshipRef, relationshipData);
  });

  return {
    relationshipId,
    senderUid: inviteData.senderUid,
    senderDisplayName: inviteData.senderDisplayName,
  };
}

export async function handleRevokeGuardianRelationship(
  request: CallableRequest<RevokeRelationshipRequest>
): Promise<{ success: boolean; relationshipId: string }> {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required to revoke relationship.");
  }

  const relationshipId = request.data?.relationshipId;
  if (!relationshipId || typeof relationshipId !== "string") {
    throw new HttpsError("invalid-argument", "Relationship ID is required.");
  }

  const db = getFirestore();
  const callerUid = request.auth.uid;
  const relRef = db.collection("relationships").doc(relationshipId);
  const relDoc = await relRef.get();

  if (!relDoc.exists) {
    throw new HttpsError("not-found", "Relationship not found.");
  }

  const relData = relDoc.data() as GuardianRelationship;

  // Only the sender or the guardian can revoke the relationship
  if (relData.senderUid !== callerUid && relData.guardianUid !== callerUid) {
    throw new HttpsError("permission-denied", "You are not authorized to revoke this relationship.");
  }

  await relRef.update({
    status: "REVOKED",
    revokedAt: FieldValue.serverTimestamp(),
    revokedBy: callerUid,
  });

  return {
    success: true,
    relationshipId,
  };
}

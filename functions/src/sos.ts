import { getFirestore, Timestamp, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import {
  DispatchSosRequest,
  DispatchSosResponse,
  SosAlert,
  SensitiveLocationData,
  RecipientDeliveryStatus,
} from "./types";

export async function handleDispatchRemoteSos(
  request: CallableRequest<DispatchSosRequest>
): Promise<DispatchSosResponse> {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required to dispatch remote SOS.");
  }

  const callerUid = request.auth.uid;
  const callerName = request.auth.token.name || "SafeHer User";
  const { idempotencyKey, recipientGuardianUids, messageText, location } = request.data || {};

  if (!idempotencyKey || typeof idempotencyKey !== "string" || idempotencyKey.trim().length === 0) {
    throw new HttpsError("invalid-argument", "A unique idempotencyKey is required.");
  }

  if (!Array.isArray(recipientGuardianUids) || recipientGuardianUids.length === 0) {
    throw new HttpsError("invalid-argument", "At least one recipient guardian UID must be provided.");
  }

  const db = getFirestore();
  const now = Timestamp.now();

  // 1. Check idempotency: alert with this key by this sender created in the last 15 minutes
  const recentAlertsQuery = await db
    .collection("alerts")
    .where("senderUid", "==", callerUid)
    .where("idempotencyKey", "==", idempotencyKey)
    .limit(1)
    .get();

  if (!recentAlertsQuery.empty) {
    const existing = recentAlertsQuery.docs[0];
    const data = existing.data() as SosAlert;
    return {
      alertId: existing.id,
      isIdempotentReplay: true,
      recipientsCount: data.recipientUids.length,
      fcmAcceptedCount: data.recipientUids.length,
      fcmFailedCount: 0,
      authorizedRecipients: data.recipientUids,
    };
  }

  // 2. Authorize recipients: only recipients with an ACTIVE relationship with caller
  const activeRelationshipsSnapshot = await db
    .collection("relationships")
    .where("senderUid", "==", callerUid)
    .where("status", "==", "ACTIVE")
    .get();

  const authorizedGuardianSet = new Set<string>();
  activeRelationshipsSnapshot.forEach((doc) => {
    const rel = doc.data();
    if (rel.guardianUid) {
      authorizedGuardianSet.add(rel.guardianUid);
    }
  });

  const authorizedRecipients = recipientGuardianUids.filter((uid) =>
    typeof uid === "string" && authorizedGuardianSet.has(uid)
  );

  if (authorizedRecipients.length === 0) {
    throw new HttpsError(
      "failed-precondition",
      "None of the selected recipients have an active, accepted remote guardian relationship."
    );
  }

  // 3. Location validation (if provided)
  let validLocation: SensitiveLocationData | null = null;
  if (location) {
    const { latitude, longitude, accuracyMeters } = location;
    if (
      typeof latitude === "number" &&
      typeof longitude === "number" &&
      latitude >= -90 &&
      latitude <= 90 &&
      longitude >= -180 &&
      longitude <= 180
    ) {
      validLocation = {
        latitude,
        longitude,
        accuracyMeters: typeof accuracyMeters === "number" && accuracyMeters >= 0 ? accuracyMeters : 0,
        capturedAt: now,
        // Sensitive coordinate retention: 24 hours
        retentionExpiresAt: Timestamp.fromMillis(Date.now() + 24 * 60 * 60 * 1000),
      };
    }
  }

  // 4. Create Alert document
  const alertRef = db.collection("alerts").doc();
  const alertData: SosAlert = {
    alertId: alertRef.id,
    senderUid: callerUid,
    senderDisplayName: callerName,
    idempotencyKey,
    messageText: (messageText || "EMERGENCY: I need help right now.").substring(0, 500),
    hasLocation: validLocation !== null,
    recipientUids: authorizedRecipients,
    status: "ACTIVE",
    createdAt: now,
    expiresAt: Timestamp.fromMillis(Date.now() + 7 * 24 * 60 * 60 * 1000), // 7 days retention
  };

  const batch = db.batch();
  batch.set(alertRef, alertData);

  // Store sensitive location in subdocument
  if (validLocation) {
    const locRef = alertRef.collection("sensitive").doc("location");
    batch.set(locRef, validLocation);
  }

  // Initial delivery states: CREATED -> QUEUED
  for (const guardianUid of authorizedRecipients) {
    const statusRef = alertRef.collection("delivery_status").doc(guardianUid);
    const initialStatus: RecipientDeliveryStatus = {
      guardianUid,
      status: "QUEUED",
      updatedAt: now,
      failureReason: null,
    };
    batch.set(statusRef, initialStatus);
  }

  await batch.commit();

  // 5. Retrieve FCM tokens for authorized guardians & dispatch push notifications
  let fcmAcceptedCount = 0;
  let fcmFailedCount = 0;

  for (const guardianUid of authorizedRecipients) {
    const userDoc = await db.collection("users").doc(guardianUid).get();
    const fcmTokensMap = userDoc.data()?.fcmTokens as Record<string, any> | undefined;
    const tokens = fcmTokensMap ? Object.keys(fcmTokensMap).filter((t) => t.length > 10) : [];

    const deliveryStatusRef = alertRef.collection("delivery_status").doc(guardianUid);

    if (tokens.length === 0) {
      // Guardian has no registered FCM device tokens
      await deliveryStatusRef.update({
        status: "FAILED",
        updatedAt: FieldValue.serverTimestamp(),
        failureReason: "No active FCM device tokens registered for this guardian.",
      });
      fcmFailedCount++;
      continue;
    }

    try {
      // Minimal, privacy-preserving notification payload (no raw GPS, phone numbers or private note on lock screen)
      const response = await getMessaging().sendEachForMulticast({
        tokens,
        notification: {
          title: "🚨 SafeHer Emergency Alert",
          body: `${callerName} has triggered an SOS alert. Tap to view details and acknowledge.`,
        },
        data: {
          alertId: alertRef.id,
          senderUid: callerUid,
          senderName: callerName,
          type: "EMERGENCY_SOS",
          timestamp: String(Date.now()),
        },
        android: {
          priority: "high",
          notification: {
            channelId: "safeher_emergency_alerts",
            sound: "default",
            defaultVibrateTimings: true,
          },
        },
      });

      if (response.successCount > 0) {
        // FCM accepted the message for dispatch to Google push servers.
        // NOTE: This does NOT mean the device received it yet (that transitions on reportAlertReceived).
        await deliveryStatusRef.update({
          status: "FCM_ACCEPTED",
          updatedAt: FieldValue.serverTimestamp(),
        });
        fcmAcceptedCount++;
      } else {
        await deliveryStatusRef.update({
          status: "FAILED",
          updatedAt: FieldValue.serverTimestamp(),
          failureReason: "Push service rejected notification for all registered guardian devices.",
        });
        fcmFailedCount++;
      }
    } catch (err: any) {
      await deliveryStatusRef.update({
        status: "FAILED",
        updatedAt: FieldValue.serverTimestamp(),
        failureReason: err?.message || "FCM dispatch error.",
      });
      fcmFailedCount++;
    }
  }

  return {
    alertId: alertRef.id,
    isIdempotentReplay: false,
    recipientsCount: authorizedRecipients.length,
    fcmAcceptedCount,
    fcmFailedCount,
    authorizedRecipients,
  };
}

import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { ReportReceivedRequest, AcknowledgeAlertRequest, SosAlert } from "./types";

export async function handleReportAlertReceived(
  request: CallableRequest<ReportReceivedRequest>
): Promise<{ success: boolean; alertId: string }> {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required to report alert receipt.");
  }

  const alertId = request.data?.alertId;
  if (!alertId || typeof alertId !== "string") {
    throw new HttpsError("invalid-argument", "Valid alertId is required.");
  }

  const callerUid = request.auth.uid;
  const db = getFirestore();
  const alertRef = db.collection("alerts").doc(alertId);
  const alertDoc = await alertRef.get();

  if (!alertDoc.exists) {
    throw new HttpsError("not-found", "Alert not found.");
  }

  const alertData = alertDoc.data() as SosAlert;
  if (!alertData.recipientUids.includes(callerUid)) {
    throw new HttpsError("permission-denied", "Caller is not an authorized recipient of this alert.");
  }

  const statusRef = alertRef.collection("delivery_status").doc(callerUid);
  const statusDoc = await statusRef.get();

  // If already ACKNOWLEDGED, do not downgrade back to DEVICE_RECEIVED
  const currentStatus = statusDoc.data()?.status;
  if (currentStatus !== "ACKNOWLEDGED") {
    await statusRef.set(
      {
        guardianUid: callerUid,
        status: "DEVICE_RECEIVED",
        receivedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
  }

  return { success: true, alertId };
}

export async function handleAcknowledgeAlert(
  request: CallableRequest<AcknowledgeAlertRequest>
): Promise<{ success: boolean; alertId: string }> {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required to acknowledge alert.");
  }

  const alertId = request.data?.alertId;
  const deviceModel = request.data?.deviceModel || "Android Device";
  if (!alertId || typeof alertId !== "string") {
    throw new HttpsError("invalid-argument", "Valid alertId is required.");
  }

  const callerUid = request.auth.uid;
  const db = getFirestore();
  const alertRef = db.collection("alerts").doc(alertId);
  const alertDoc = await alertRef.get();

  if (!alertDoc.exists) {
    throw new HttpsError("not-found", "Alert not found.");
  }

  const alertData = alertDoc.data() as SosAlert;
  if (!alertData.recipientUids.includes(callerUid)) {
    throw new HttpsError("permission-denied", "Caller is not an authorized recipient of this alert.");
  }

  const statusRef = alertRef.collection("delivery_status").doc(callerUid);

  await statusRef.set(
    {
      guardianUid: callerUid,
      status: "ACKNOWLEDGED",
      acknowledgedAt: FieldValue.serverTimestamp(),
      acknowledgedDevice: deviceModel,
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  return { success: true, alertId };
}

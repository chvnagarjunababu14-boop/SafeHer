import { initializeApp } from "firebase-admin/app";
import { onCall, CallableRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import {
  handleCreateGuardianInvite,
  handleAcceptGuardianInvite,
  handleRevokeGuardianRelationship,
} from "./invitations";
import { handleDispatchRemoteSos } from "./sos";
import { handleReportAlertReceived, handleAcknowledgeAlert } from "./acknowledgements";
import { purgeExpiredSensitiveLocations } from "./cleanup";

// Initialize Firebase Admin SDK
initializeApp();

// Configuration options for 2nd Gen functions
// Enforce App Check when enabled in Firebase Console; in dev/testing App Check can be optional
const CALLABLE_OPTIONS = {
  region: "asia-south1", // Default region, adjustable in firebase.json
  enforceAppCheck: false, // Set to true once Play Integrity / debug token is registered in Firebase Console
};

export const createGuardianInvite = onCall(CALLABLE_OPTIONS, (request: CallableRequest) => {
  return handleCreateGuardianInvite(request);
});

export const acceptGuardianInvite = onCall(CALLABLE_OPTIONS, (request: CallableRequest<any>) => {
  return handleAcceptGuardianInvite(request);
});

export const revokeGuardianRelationship = onCall(CALLABLE_OPTIONS, (request: CallableRequest<any>) => {
  return handleRevokeGuardianRelationship(request);
});

export const dispatchRemoteSos = onCall(CALLABLE_OPTIONS, (request: CallableRequest<any>) => {
  return handleDispatchRemoteSos(request);
});

export const reportAlertReceived = onCall(CALLABLE_OPTIONS, (request: CallableRequest<any>) => {
  return handleReportAlertReceived(request);
});

export const acknowledgeAlert = onCall(CALLABLE_OPTIONS, (request: CallableRequest<any>) => {
  return handleAcknowledgeAlert(request);
});

// Scheduled cleanup for sensitive location retention: runs every 6 hours
export const scheduledLocationCleanup = onSchedule(
  { schedule: "every 6 hours", region: "asia-south1" },
  async () => {
    const purgedCount = await purgeExpiredSensitiveLocations();
    console.log(`[SafeHer Retention] Purged ${purgedCount} expired sensitive location subdocuments.`);
  }
);

// Callable version for testing cleanup
export const triggerLocationCleanup = onCall(CALLABLE_OPTIONS, async () => {
  const purged = await purgeExpiredSensitiveLocations();
  return { purgedCount: purged };
});

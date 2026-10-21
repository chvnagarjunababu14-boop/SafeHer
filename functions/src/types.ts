import { Timestamp } from "firebase-admin/firestore";

export type DeliveryStatus =
  | "CREATED"
  | "QUEUED"
  | "FCM_ACCEPTED"
  | "DEVICE_RECEIVED"
  | "ACKNOWLEDGED"
  | "FAILED"
  | "UNKNOWN";

export interface UserProfile {
  uid: string;
  email: string | null;
  displayName: string | null;
  fcmTokens?: Record<string, { deviceModel?: string; updatedAt: Timestamp }>;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface GuardianInvitation {
  id: string;
  code: string;
  senderUid: string;
  senderEmail: string;
  senderDisplayName: string;
  status: "PENDING" | "ACCEPTED" | "EXPIRED" | "REVOKED";
  createdAt: Timestamp;
  expiresAt: Timestamp;
  acceptedByUid?: string | null;
  acceptedAt?: Timestamp | null;
}

export interface GuardianRelationship {
  id: string; // ${senderUid}_${guardianUid}
  senderUid: string;
  senderDisplayName: string;
  senderEmail: string;
  guardianUid: string;
  guardianDisplayName: string;
  guardianEmail: string;
  status: "ACTIVE" | "REVOKED";
  createdAt: Timestamp;
  revokedAt?: Timestamp | null;
  revokedBy?: string | null;
}

export interface SensitiveLocationData {
  latitude: number;
  longitude: number;
  accuracyMeters: number;
  capturedAt: Timestamp;
  retentionExpiresAt: Timestamp;
}

export interface SosAlert {
  alertId: string;
  senderUid: string;
  senderDisplayName: string;
  idempotencyKey: string;
  messageText: string;
  hasLocation: boolean;
  recipientUids: string[];
  status: "ACTIVE" | "RESOLVED" | "CANCELLED";
  createdAt: Timestamp;
  expiresAt: Timestamp;
}

export interface RecipientDeliveryStatus {
  guardianUid: string;
  status: DeliveryStatus;
  updatedAt: Timestamp;
  failureReason?: string | null;
  receivedAt?: Timestamp | null;
  acknowledgedAt?: Timestamp | null;
  acknowledgedDevice?: string | null;
}

// Request and Response payloads
export interface CreateInviteRequest {
  // empty or optional note
}

export interface CreateInviteResponse {
  inviteId: string;
  code: string;
  expiresAtMillis: number;
}

export interface AcceptInviteRequest {
  code: string;
}

export interface AcceptInviteResponse {
  relationshipId: string;
  senderUid: string;
  senderDisplayName: string;
}

export interface RevokeRelationshipRequest {
  relationshipId: string;
}

export interface DispatchSosRequest {
  idempotencyKey: string;
  recipientGuardianUids: string[];
  messageText?: string;
  location?: {
    latitude: number;
    longitude: number;
    accuracyMeters: number;
  };
}

export interface DispatchSosResponse {
  alertId: string;
  isIdempotentReplay: boolean;
  recipientsCount: number;
  fcmAcceptedCount: number;
  fcmFailedCount: number;
  authorizedRecipients: string[];
}

export interface ReportReceivedRequest {
  alertId: string;
}

export interface AcknowledgeAlertRequest {
  alertId: string;
  deviceModel?: string;
}

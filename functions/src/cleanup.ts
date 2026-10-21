import { getFirestore, Timestamp } from "firebase-admin/firestore";

/**
 * Purges sensitive location subdocuments where retentionExpiresAt has passed.
 * Protects user privacy by ensuring precise GPS coordinates are not retained indefinitely.
 */
export async function purgeExpiredSensitiveLocations(): Promise<number> {
  const db = getFirestore();
  const now = Timestamp.now();

  const expiredLocationQuery = await db
    .collectionGroup("sensitive")
    .where("retentionExpiresAt", "<=", now)
    .get();

  if (expiredLocationQuery.empty) {
    return 0;
  }

  const batch = db.batch();
  expiredLocationQuery.docs.forEach((doc) => {
    batch.delete(doc.ref);
  });

  await batch.commit();
  return expiredLocationQuery.size;
}

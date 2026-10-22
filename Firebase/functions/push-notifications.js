import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';

let draining = false;
// The transaction commits assignments and jobs together. Sending happens afterwards,
// so an FCM outage cannot roll back an already committed trip or duplicate its assignment.
export async function drainPushJobs() {
  if (draining) return;
  draining = true;
  const db = getFirestore();
  try {
    const jobs = await db.collection('pushJobs').where('nextAt', '<=', Date.now()).limit(50).get();
    for (const doc of jobs.docs) {
      const now = Date.now(), job = await db.runTransaction(async tx => {
        const snapshot = await tx.get(doc.ref), value = snapshot.data();
        if (!value || value.state !== 'pending' || value.nextAt > now || value.leaseUntil > now) return null;
        tx.update(doc.ref, { leaseUntil: now + 120_000 });
        return value;
      });
      if (!job) continue;
      try {
        const [trip, driver] = await db.getAll(db.doc(`dispatchTrips/${job.tripId}`), db.doc(`drivers/${job.driverId}`));
        if (job.expiresAt <= now || driver.data()?.accountStatus !== 'active' || trip.data()?.driverId !== job.driverId ||
            trip.data()?.assignmentVersion !== job.assignmentVersion || !['assigned', 'handover_pending'].includes(trip.data()?.status)) {
          await doc.ref.update({ state: 'obsolete', leaseUntil: 0, nextAt: FieldValue.delete() }); continue;
        }
        const devices = await db.collection('pushDevices').doc(job.driverId).collection('devices').limit(20).get();
        if (devices.empty) { await doc.ref.update({ leaseUntil: 0, nextAt: now + 60_000 }); continue; }
        let retry = false;
        for (const device of devices.docs) {
          try {
            await getMessaging().send({ token: device.data().token,
              data: { type: 'trip_assignment', driverId: job.driverId, tripId: job.tripId,
                assignmentVersion: String(job.assignmentVersion), eventId: doc.id },
              android: { priority: 'high', ttl: Math.max(0, job.expiresAt - now) } });
          } catch (error) {
            if (['messaging/registration-token-not-registered', 'messaging/invalid-registration-token'].includes(error.code)) await device.ref.delete();
            else { retry = true; console.warn('Push delivery deferred', error.code || 'unknown'); }
          }
        }
        const attempts = (job.attempts || 0) + 1;
        await doc.ref.update({ state: retry ? (attempts >= 8 ? 'failed' : 'pending') : 'sent', attempts,
          leaseUntil: 0, nextAt: retry && attempts < 8 ? now + Math.min(300_000, 15_000 * 2 ** attempts) : FieldValue.delete(), updatedAt: Date.now() });
      } catch (error) {
        await doc.ref.update({ leaseUntil: 0, nextAt: Date.now() + 60_000 }).catch(() => {});
        console.warn('Push job deferred', error.code || 'unknown');
      }
    }
  } catch (error) { console.warn('Push queue unavailable', error.code || 'unknown'); }
  finally { draining = false; }
}

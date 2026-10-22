import { useEffect, useState } from 'react';
import { doc, getDoc } from 'firebase/firestore';
import { db } from '../api/firebase';

type Identity = { displayName?: string; email?: string };
const profiles = new Map<string, Promise<Identity>>();
export default function StaffIdentity({ uid, name, email }: { uid: string; name?: string; email?: string }) {
  const [profile, setProfile] = useState<Identity>({});
  useEffect(() => {
    let current = true; setProfile({});
    if (!name && !email && uid) {
      // Legacy records only have a UID. Future records use the server's identity snapshot.
      if (!profiles.has(uid)) profiles.set(uid, getDoc(doc(db, 'staff', uid)).then(d => d.data() || {}).catch(() => {
        profiles.delete(uid); return {};
      }));
      void profiles.get(uid)!.then(value => { if (current) setProfile(value); });
    }
    return () => { current = false; };
  }, [uid, name, email]);
  return <span title={`UID: ${uid}`}>{[name || profile.displayName, email || profile.email].filter(Boolean).join(' · ') || 'Nhân viên điều phối'}</span>;
}

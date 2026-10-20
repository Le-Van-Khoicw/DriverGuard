import { createClient } from '@supabase/supabase-js';

if (!process.env.SUPABASE_URL || !process.env.SUPABASE_SERVICE_ROLE_KEY) throw new Error('Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY in the server environment');
const client = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_ROLE_KEY, { auth: { persistSession: false, autoRefreshToken: false } });
const name = process.env.SUPABASE_DOCUMENT_BUCKET || 'driver-documents';
const { data: buckets, error: listError } = await client.storage.listBuckets();
if (listError) throw new Error('Cannot list Supabase buckets. Check project and server key.');
const existing = buckets.find(bucket => bucket.id === name);
if (existing?.public) throw new Error('Existing bucket is public. Create a new private bucket instead.');
const options = { public: false, fileSizeLimit: 5 * 1024 * 1024, allowedMimeTypes: ['image/jpeg', 'image/png', 'application/pdf'] };
const { error } = existing ? await client.storage.updateBucket(name, options) : await client.storage.createBucket(name, options);
if (error) throw new Error('Cannot configure private bucket. Check server credentials and Storage permissions.');
console.log('Private driver-documents bucket is configured. Do not add anonymous/public access policies.');

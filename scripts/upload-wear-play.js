#!/usr/bin/env node
/**
 * Upload Wear AAB only to wear:* track (never phone internal/beta/production).
 * Replaces r0adkll/upload-google-play for Wear (avoids stray copies on phone beta).
 */
const fs = require('fs');

function parseArgs() {
  const args = process.argv.slice(2);
  const out = {
    package: 'com.eazpire.creator.wear',
    aab: 'app/build/outputs/bundle/release/app-release.aab',
    track: 'wear:internal',
    symbols: '',
    status: 'completed',
  };
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--package') out.package = args[++i];
    else if (args[i] === '--aab') out.aab = args[++i];
    else if (args[i] === '--track') out.track = args[++i];
    else if (args[i] === '--symbols') out.symbols = args[++i];
    else if (args[i] === '--status') out.status = args[++i];
  }
  if (!out.track.startsWith('wear:')) {
    console.error(`::error::Refusing phone track "${out.track}" — use wear:internal`);
    process.exit(1);
  }
  return out;
}

async function getPublisher() {
  const raw = process.env.PLAY_SERVICE_ACCOUNT_JSON;
  if (!raw) throw new Error('PLAY_SERVICE_ACCOUNT_JSON missing');
  const { google } = await import('googleapis');
  const auth = new google.auth.GoogleAuth({
    credentials: JSON.parse(raw),
    scopes: ['https://www.googleapis.com/auth/androidpublisher'],
  });
  return google.androidpublisher({ version: 'v3', auth });
}

async function getTrackReleases(publisher, packageName, editId, track) {
  const res = await publisher.edits.tracks.list({ packageName, editId });
  const row = (res.data.tracks || []).find((t) => t.track === track);
  return row?.releases || [];
}

/**
 * Play allows only one release with status "completed" per track.
 * Merge all versionCodes (existing + new) into a single completed release.
 */
async function assignVersionToTrack(publisher, packageName, editId, track, versionCode, status) {
  const existing = await getTrackReleases(publisher, packageName, editId, track);
  const codeStr = String(versionCode);
  const versionCodes = new Set();
  for (const rel of existing) {
    for (const vc of rel.versionCodes || []) versionCodes.add(String(vc));
  }
  if (versionCodes.has(codeStr)) {
    console.log(`versionCode ${versionCode} already assigned to ${track}`);
    return;
  }
  versionCodes.add(codeStr);

  const sorted = [...versionCodes].sort((a, b) => Number(a) - Number(b));
  const releases = [{ status, versionCodes: sorted }];

  await publisher.edits.tracks.update({
    packageName,
    editId,
    track,
    requestBody: { track, releases },
  });
  console.log(
    `Assigned versionCode ${versionCode} to ${track} (one ${status} release, versionCodes: ${sorted.join(', ')})`
  );
}

function formatPlayApiError(e) {
  const parts = [e.message || String(e)];
  const data = e.response?.data;
  if (data) {
    if (typeof data === 'string') parts.push(data);
    else parts.push(JSON.stringify(data));
  }
  return parts.join(' — ');
}

async function uploadNativeSymbols(publisher, packageName, editId, versionCode, symbolsPath) {
  if (!symbolsPath || !fs.existsSync(symbolsPath)) return;
  const data = fs.readFileSync(symbolsPath);
  await publisher.edits.deobfuscationfiles.upload({
    packageName,
    editId,
    apkVersionCode: versionCode,
    deobfuscationFileType: 'nativeCode',
    media: {
      mimeType: 'application/octet-stream',
      body: require('stream').Readable.from(data),
    },
  });
  console.log(`Uploaded native debug symbols for versionCode ${versionCode}`);
}

async function main() {
  const opts = parseArgs();
  if (!fs.existsSync(opts.aab)) throw new Error(`AAB not found: ${opts.aab}`);

  const publisher = await getPublisher();
  const insert = await publisher.edits.insert({ packageName: opts.package });
  const editId = insert.data.id;
  console.log(`Edit ${editId} — upload to track ${opts.track} only`);

  try {
    const bundle = await publisher.edits.bundles.upload({
      packageName: opts.package,
      editId,
      media: {
        mimeType: 'application/octet-stream',
        body: fs.createReadStream(opts.aab),
      },
    });
    const versionCode = Number(bundle.data.versionCode);
    if (!versionCode) throw new Error('Bundle upload returned no versionCode');
    console.log(`Uploaded AAB versionCode=${versionCode}`);

    await uploadNativeSymbols(publisher, opts.package, editId, versionCode, opts.symbols);

    await assignVersionToTrack(
      publisher,
      opts.package,
      editId,
      opts.track,
      versionCode,
      opts.status
    );

    const PHONE = new Set(['internal', 'alpha', 'beta', 'production', 'rollout']);
    const listed = await publisher.edits.tracks.list({ packageName: opts.package, editId });
    for (const t of listed.data.tracks || []) {
      const name = t.track || '';
      if (name.startsWith('wear:') || !PHONE.has(name)) continue;
      const next = [];
      let changed = false;
      for (const rel of t.releases || []) {
        const kept = (rel.versionCodes || []).map(String).filter((c) => Number(c) < 2);
        if (kept.length !== (rel.versionCodes || []).length) changed = true;
        if (kept.length) next.push({ ...rel, versionCodes: kept });
      }
      if (!changed) continue;
      await publisher.edits.tracks.update({
        packageName: opts.package,
        editId,
        track: name,
        requestBody: { track: name, releases: next },
      });
      console.log(`Pruned Wear codes from phone track ${name}`);
    }

    await publisher.edits.commit({ packageName: opts.package, editId });
    console.log('Edit committed.');
  } catch (e) {
    try {
      await publisher.edits.delete({ packageName: opts.package, editId });
    } catch {
      /* ignore */
    }
    throw e;
  }
}

main().catch((e) => {
  const msg = formatPlayApiError(e);
  console.error(msg);
  console.error(`::error::${msg.replace(/[\r\n]+/g, ' ').slice(0, 500)}`);
  process.exit(1);
});

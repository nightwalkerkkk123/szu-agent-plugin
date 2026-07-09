const fs = require('fs');
const path = process.argv[2] || 'C:/Users/王子豪/Downloads/ehall.szu.edu.cn11new.har';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const entries = data.log.entries;

function getBody(e) {
  const content = e.response?.content;
  if (!content) return '';
  if (content.encoding === 'base64') {
    return Buffer.from(content.text, 'base64').toString('utf8');
  }
  return content.text || '';
}

function parseReqBody(e) {
  const text = e.request?.postData?.text || '';
  try {
    return JSON.parse(text);
  } catch {
    try {
      return Object.fromEntries(new URLSearchParams(text));
    } catch {
      return { raw: text.slice(0, 200) };
    }
  }
}

console.log(`Total entries: ${entries.length}\n`);

// 1. List all unique API endpoints
const apiUrls = new Set();
for (const e of entries) {
  const url = e.request.url;
  if (url.includes('szu.edu.cn')) {
    const base = url.split('?')[0];
    apiUrls.add(`${e.request.method} ${base}`);
  }
}
console.log('--- All API endpoints ---');
for (const u of [...apiUrls].sort()) console.log(u);

// 2. Extract sport/room/time info
const xmdmNames = new Map();
const timeSlotsByXmdm = new Map();
const rooms = [];
let dates = [];

for (const e of entries) {
  const url = e.request.url;
  const req = parseReqBody(e);
  const resp = getBody(e);
  let respObj = null;
  try { respObj = JSON.parse(resp); } catch {}

  if (url.includes('getRqList.do')) {
    dates = respObj || [];
  } else if (url.includes('getTimeList.do')) {
    const xmdm = req.XMDM || '?';
    const slots = respObj || [];
    if (!timeSlotsByXmdm.has(xmdm)) {
      timeSlotsByXmdm.set(xmdm, []);
    }
    timeSlotsByXmdm.get(xmdm).push({ yyrq: req.YYRQ, slots });
  } else if (url.includes('getOpeningRoom.do')) {
    const rows = respObj?.datas?.getOpeningRoom?.rows || [];
    for (const r of rows) {
      rooms.push(r);
      if (r.XMDM_DISPLAY && r.XMDM) xmdmNames.set(r.XMDM, r.XMDM_DISPLAY);
      if (r.XQDM_DISPLAY && r.XQDM) xmdmNames.set('XQDM_' + r.XQDM, r.XQDM_DISPLAY);
    }
  }
}

console.log('\n--- Available dates ---');
console.log(dates);

console.log('\n--- Room / venue info ---');
for (const r of rooms) {
  console.log(`  ${r.XQDM_DISPLAY}(${r.XQDM}) / ${r.XMDM_DISPLAY}(${r.XMDM}) / ${r.CGBM_DISPLAY}(${r.CGBM}) / ${r.CDMC} / 容量=${r.SCWSDPRS} / 状态=${r.STATE_EXPLAIN}`);
}

console.log('\n--- Time slots by XMDM ---');
for (const [xmdm, list] of timeSlotsByXmdm) {
  const name = xmdmNames.get(xmdm) || '?';
  console.log(`\nXMDM=${xmdm} (${name}) — ${list.length} request(s)`);
  for (const item of list.slice(0, 2)) {
    console.log(`  Date ${item.yyrq}: ${item.slots.length} slots`);
    for (const s of item.slots.slice(0, 5)) {
      console.log(`    ${s.CODE} | ${s.STATE_EXPLAIN || 'null'} | ${s.text}`);
    }
    if (item.slots.length > 5) console.log(`    ... and ${item.slots.length - 5} more`);
  }
}

// 3. Try to find any request that returns the sport list / XMDM mapping
console.log('\n--- URLs that might return sport/venue list ---');
for (const e of entries) {
  const url = e.request.url;
  if (/getXmdm|getYcList|getXqList|getSport|sportList|venueList|querySport|queryXmdm/i.test(url)) {
    console.log(e.request.method, url);
    console.log('  response:', getBody(e).slice(0, 400));
  }
}

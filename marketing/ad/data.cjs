// Bundles what the page needs into assets/data.js: each clip's frame count and the moments read
// from it (assets/clips/*.json), and the music's moments (out/music.json, written by music.py from
// the same marks). Refuses recordings that no longer fit their scenes.
const fs = require('fs');
const path = require('path');
const here = __dirname;
const json = (f) => JSON.parse(fs.readFileSync(path.join(here, f), 'utf8'));
const count = (c) => fs.readdirSync(path.join(here, 'frames/clips', c)).filter((f) => f.endsWith('.jpg')).length;
const clips = {};
for (const c of ['airplane-bill', 'paella', 'poco-fit']) clips[c] = { fps: 30, count: count(c) };
const air = json('assets/clips/airplane-bill.json').clip_marks, paella = json('assets/clips/paella.json').clip_marks;
const poco = json('assets/clips/poco-fit.json').clip_marks, music = json('out/music.json');
const fail = (m) => { console.error('data.cjs: ' + m); process.exit(1); };
if (!(air.qs_open < air.airplane_on && air.airplane_on < air.sent && air.sent < air.first_text)) fail('airplane marks out of order');
if (!(paella.sent < paella.first_text && paella.first_text < paella.done)) fail('paella marks out of order');
if (clips['airplane-bill'].count / 30 < air.first_text + 4) fail('airplane clip too short for its answer');
fs.writeFileSync(path.join(here, 'assets/data.js'), 'window.AD_DATA = ' + JSON.stringify({ clips, air, paella, poco, music }, null, 1) + ';\n');
console.log('data.js:', Object.entries(clips).map(([k, v]) => `${k} ${v.count}`).join(', '));

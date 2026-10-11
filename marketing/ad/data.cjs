// Bundles what the page needs to know about the recordings into assets/data.js: how many frames
// each clip has and the moments read from it (assets/clips/*.json). Refuses recordings that no
// longer fit their scenes, so a re-recorded take cannot quietly break the ad.
const fs = require('fs');
const path = require('path');
const here = __dirname;
const json = (f) => JSON.parse(fs.readFileSync(path.join(here, 'assets/clips', f), 'utf8'));
const count = (c) => fs.readdirSync(path.join(here, 'frames/clips', c)).filter((f) => f.endsWith('.jpg')).length;
const clips = {};
for (const c of ['npu-lease', 'tools', 'fit', 'poco-fit', 'canvas-ask', 'canvas-write', 'canvas-run']) clips[c] = { fps: 30, count: count(c) };
const lease = json('npu-lease.json'), tools = json('tools.json'), fit = json('fit.json'), poco = json('poco-fit.json'), canvas = json('canvas.json');
const fail = (m) => { console.error('data.cjs: ' + m); process.exit(1); };
const m = lease.clip_marks;
if (!(m.sent < m.first_text && m.first_text < m.catches_heading && m.catches_heading < m.arithmetic_slip_from)) fail('lease marks out of order');
if (clips['npu-lease'].count / 30 < m.catches_heading) fail('lease clip ends before its marks');
if (tools.clip_marks.off.length !== 3) fail('expected three switches');
if (!(fit.clip_marks.search < fit.clip_marks.open)) fail('fit marks out of order');
const data = {
  clips,
  lease: { ...m, runtime: lease.runtime_log, footer: lease.app_footer, model: lease.model, device: lease.device },
  tools: tools.clip_marks,
  fit: fit.clip_marks,
  poco: poco.clip_marks,
  canvas: canvas.clip_marks,
};
fs.writeFileSync(path.join(here, 'assets/data.js'), 'window.AD_DATA = ' + JSON.stringify(data, null, 1) + ';\n');
console.log('data.js:', Object.entries(clips).map(([k, v]) => `${k} ${v.count}`).join(', '));

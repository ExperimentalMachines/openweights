// Picture check, for every event in out/music.json, rendered 50 ms after its sound:
//  - an element event: the element is visible (opacity through all its ancestors above 0.5, not
//    hidden, at least 80% inside the frame, and its centre not under the lime wipe);
//  - a cut: over 35% of what is drawn (pixels not background white in either frame) changes across
//    it; the phone shell and the airplane inset carry over a cut inside one scene, so not most;
//  - a logo note: its bar has grown past half its length 100 ms after the note.
// A sound with nothing to see fails.
const { chromium } = require('playwright');
const m = require('./out/music.json');
(async () => {
  const b = await chromium.launch();
  const p = await (await b.newContext({ viewport: { width: 1920, height: 1080 } })).newPage();
  await p.goto('file://' + __dirname + '/ad.html');
  await p.evaluate(() => window.ready);
  const grab = async (t) => {
    await p.evaluate((t) => window.renderAt(t), t);
    return p.screenshot({ type: 'png' });
  };
  const diff = (a, c) => p.evaluate(async ([a, c]) => {
    const load = async (s) => { const i = new Image(); i.src = 'data:image/png;base64,' + s; await i.decode(); return i; };
    const [ia, ic] = await Promise.all([load(a), load(c)]);
    const cv = new OffscreenCanvas(192, 108), g = cv.getContext('2d');
    g.drawImage(ia, 0, 0, 192, 108); const da = g.getImageData(0, 0, 192, 108).data;
    g.drawImage(ic, 0, 0, 192, 108); const dc = g.getImageData(0, 0, 192, 108).data;
    let changed = 0, drawn = 0;
    const white = (d, i) => d[i] > 245 && d[i + 1] > 245 && d[i + 2] > 245;
    for (let i = 0; i < da.length; i += 4) {
      if (white(da, i) && white(dc, i)) continue;
      drawn++;
      changed += Math.abs(da[i] - dc[i]) + Math.abs(da[i + 1] - dc[i + 1]) + Math.abs(da[i + 2] - dc[i + 2]) > 60;
    }
    return changed / Math.max(1, drawn);
  }, [a.toString('base64'), c.toString('base64')]);
  let bad = 0, n = { el: 0, cut: 0, bar: 0 };
  for (const e of m.events) {
    if (!e.key) continue;
    let ok, what;
    if (e.key === 'cut') {
      const f = await diff(await grab(e.t - 0.03), await grab(e.t + 0.03));
      ok = f > 0.35; what = `cut, ${(f * 100).toFixed(0)}% of what is drawn changes`; n.cut++;
    } else {
      await p.evaluate((t) => window.renderAt(t), e.t + 0.05);
      const v = await p.evaluate((k) => window.eventVisibility(k), e.key);
      ok = v.opacity > 0.5 && v.inside >= 0.8 && !v.underWipe;
      what = `opacity ${v.opacity.toFixed(2)}, ${(v.inside * 100).toFixed(0)}% in frame${v.underWipe ? ', under the wipe' : ''}`; n.el++;
    }
    bad += !ok;
    console.log(`${e.t.toFixed(3)}  ${e.picture.slice(0, 44).padEnd(44)} ${e.key.padEnd(7)} ${what} ${ok ? 'ok' : 'NOT VISIBLE'}`);
  }
  for (const h of m.logo_hits) {
    await p.evaluate((t) => window.renderAt(t), h.t + 0.1);
    const s = await p.evaluate((k) => window.barScale(k), h.key);
    const ok = s > 0.5; bad += !ok; n.bar++;
    console.log(`${h.t.toFixed(3)}  ${h.picture.padEnd(44)} ${h.key.padEnd(7)} bar at ${(s * 100).toFixed(0)}% ${ok ? 'ok' : 'NOT DRAWN'}`);
  }
  console.log(`${n.el} element, ${n.cut} cut and ${n.bar} logo-bar checks: ` + (bad ? `${bad} with nothing on screen` : 'every sound has its picture'));
  await b.close();
  process.exit(bad ? 1 : 0);
})();

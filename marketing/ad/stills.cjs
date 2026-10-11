// Stills at given times, for review: node stills.cjs 2.9 11.2 26.5 -> frames/still-*.png
const { chromium } = require('playwright');
(async () => {
  const times = process.argv.slice(2).map(Number);
  const b = await chromium.launch();
  const p = await (await b.newContext({ viewport: { width: 1920, height: 1080 } })).newPage();
  const errors = [];
  p.on('pageerror', (e) => errors.push(e.message));
  p.on('console', (m) => m.type() === 'error' && errors.push(m.text()));
  require('fs').mkdirSync(__dirname + '/frames', { recursive: true });
  await p.goto('file://' + __dirname + '/ad.html');
  await p.evaluate(() => window.ready);
  for (const t of times) {
    await p.evaluate((t) => window.renderAt(t), t);
    await p.screenshot({ path: `${__dirname}/frames/still-${String(t).replace('.', '_')}.png` });
  }
  console.log('stills:', times.join(', '), '| errors:', errors.length ? errors : 'none');
  await b.close();
})();

import pw from '/usr/local/lib/node_modules/playwright/index.js';
const { chromium } = pw;

const BASE = process.env.BASE || 'http://127.0.0.1:41001';

async function measure(page, scheme) {
  await page.emulateMedia({ colorScheme: scheme });
  await page.setViewportSize({ width: 390, height: 800 });
  await page.goto(`${BASE}/#/reports?period=QUARTER&year=2026&quarter=3`, { waitUntil: 'networkidle' });
  await page.waitForSelector('table', { timeout: 5000 });
  await page.waitForTimeout(300);
  return await page.evaluate(() => {
    const de = document.documentElement;
    const offenders = [];
    document.querySelectorAll('*').forEach((el) => {
      if (el.scrollWidth > document.documentElement.clientWidth + 1 &&
          el.getBoundingClientRect().right > document.documentElement.clientWidth + 1) {
        offenders.push({
          tag: el.tagName.toLowerCase(),
          cls: (el.className || '').toString().slice(0, 40),
          sw: el.scrollWidth,
          right: Math.round(el.getBoundingClientRect().right),
        });
      }
    });
    return {
      docScrollWidth: de.scrollWidth,
      docClientWidth: de.clientWidth,
      bodyScrollWidth: document.body.scrollWidth,
      offenders: offenders.slice(0, 8),
    };
  });
}

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext();
  const page = await ctx.newPage();

  // Log in first.
  await page.goto(`${BASE}/#/login`, { waitUntil: 'networkidle' });
  await page.fill('#username, input[name="username"], input', 'admin').catch(() => {});
  const inputs = await page.$$('input');
  await inputs[0].fill('admin');
  await inputs[1].fill('admin123');
  await page.click('button:has-text("Sign in")');
  await page.waitForTimeout(600);

  for (const scheme of ['light', 'dark']) {
    const r = await measure(page, scheme);
    console.log(`\n[${scheme}] doc.scrollWidth=${r.docScrollWidth} doc.clientWidth=${r.docClientWidth} body.scrollWidth=${r.bodyScrollWidth}`);
    console.log(`[${scheme}] overflow = ${r.docScrollWidth === r.docClientWidth ? 'NONE ✅' : (r.docScrollWidth - r.docClientWidth) + 'px ❌'}`);
    if (r.offenders.length) console.log(`[${scheme}] offenders:`, JSON.stringify(r.offenders));
  }
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });

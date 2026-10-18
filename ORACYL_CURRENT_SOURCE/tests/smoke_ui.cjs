const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

(async () => {
  const root = path.resolve(__dirname, '..');
  const html = fs.readFileSync(path.join(root, 'app/src/main/assets/index.html'), 'utf8');
  const adapter = fs.readFileSync(path.join(root, 'app/src/main/assets/oracyl_native.js'), 'utf8');
  const browser = await chromium.launch({headless: true, args: ['--no-sandbox']});
  for (const [name, width, height, input, button] of [
    ['phone', 360, 780, 'mInput', 'mVoice'],
    ['dex', 1920, 1080, 'chatInput', 'voice'],
    ['windowed', 1024, 768, 'chatInput', 'voice'],
  ]) {
    const page = await browser.newPage({viewport: {width, height}});
    const errors = [];
    page.on('pageerror', error => errors.push(String(error)));
    await page.setContent(html, {waitUntil: 'domcontentloaded'});
    await page.addScriptTag({content: adapter});
    if (!await page.evaluate(() => !!window.OracylHarness)) throw new Error(`${name}: adapter missing`);
    if (!await page.evaluate(async () => !(await OracylHarness.invoke('status')).ok)) throw new Error(`${name}: preview faked host`);
    await page.locator(`#${input}`).fill('No phantom backend success');
    await page.locator(`#${button}`).click({force: true});
    await page.waitForTimeout(30);
    if (errors.length) throw new Error(`${name}: ${errors.join('; ')}`);
    console.log(`${name} PASS viewport ${width}x${height} errors 0`);
    await page.close();
  }
  await browser.close();
})().catch(error => { console.error(error); process.exit(1); });
from pathlib import Path
try:
 from playwright.sync_api import sync_playwright
except ModuleNotFoundError:
 print('SKIP: Playwright Python package is unavailable in this worker environment')
 raise SystemExit(0)
root=Path(__file__).resolve().parents[1]
ui=(root/'app/src/main/assets/index.html').as_uri()
js=root/'app/src/main/assets/oracyl_native.js'
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True,executable_path='/usr/bin/chromium',args=['--no-sandbox'])
 for name,w,h,send_field,send_btn in [('phone',360,780,'mInput','mVoice'),('dex',1920,1080,'chatInput','voice'),('windowed',1024,768,'chatInput','voice')]:
  page=browser.new_page(viewport={'width':w,'height':h})
  errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)))
  page.set_content((root/'app/src/main/assets/index.html').read_text(),wait_until='domcontentloaded')
  page.add_script_tag(content=js.read_text())
  assert page.evaluate('!!window.OracylHarness'),'adapter missing'
  assert page.evaluate('OracylHarness.invoke("status").then(r=>r.ok===false)'),'preview must not fake a host'
  page.locator('#'+send_field).fill('Test should not appear as a successful backend send')
  page.locator('#'+send_btn).click(force=True)
  assert page.evaluate('OracylHarness.invoke("send",{text:"check"}).then(r=>r.ok===false)')
  assert not errors,(name,errors)
  print(name,'PASS', 'viewport',w,h,'errors',len(errors))
  page.close()
 browser.close()
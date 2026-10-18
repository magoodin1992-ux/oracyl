const assert = require('assert');
const fs = require('fs');
const vm = require('vm');
const path = require('path');
const listeners = new Map();
const makeElement = () => ({
  value: '', textContent: '', className: '', children: [],
  classList: {add(){}, remove(){}}, setAttribute(){}, removeAttribute(){},
  addEventListener(type, fn){ listeners.set(type, fn); }, appendChild(node){ this.children.push(node); },
});
const elements = new Map([['toast',makeElement()],['chatInput',makeElement()],['voice',makeElement()],
  ['conversationAppend',makeElement()],['mInput',makeElement()],['mVoice',makeElement()],['mNewMessages',makeElement()]]);
const context = {
  console, setTimeout, clearTimeout,
  crypto: require('crypto').webcrypto,
  sessionStorage: {data:new Map(),getItem(k){return this.data.get(k)||null},setItem(k,v){this.data.set(k,v)}},
  document: {getElementById:id=>elements.get(id)||null, createElement:()=>makeElement(), querySelectorAll:()=>[]},
};
context.globalThis = context; context.window = context;
vm.createContext(context);
const source = fs.readFileSync(path.resolve(__dirname,'../app/src/main/assets/oracyl_native.js'),'utf8');
vm.runInContext(source, context);
(async()=>{
  assert.deepStrictEqual(Array.from(context.OracylHarness.categories), ['Chats','Research','Tasks','Apps','Tabs','Projects','Skills','Accounts']);
  const unavailable = await context.OracylHarness.invoke('status');
  assert.strictEqual(unavailable.ok,false);
  assert.match(unavailable.error,/preview only/);
  const nativeContext = {...context};
  nativeContext.OracylHost = {dispatch(json,id){
    const request=JSON.parse(json);
    setTimeout(()=>nativeContext.OracylNativeResult(id,JSON.stringify({ok:true,action:request.action})),0);
  }};
  nativeContext.globalThis=nativeContext;nativeContext.window=nativeContext;
  vm.createContext(nativeContext);vm.runInContext(source,nativeContext);
  const response=await nativeContext.OracylHarness.invoke('status');
  assert.deepStrictEqual(JSON.parse(JSON.stringify(response)),{ok:true,action:'status'});
  console.log('NATIVE ADAPTER PASS: async callback, truthful preview failure, exact navigation order');
})().catch(error=>{console.error(error);process.exit(1)});
/* Trusted Oracyl UI adapter. All native calls are asynchronous and explicit. */
(()=>{'use strict';
 const host=globalThis.OracylHost, pending=new Map();
 const callbackId=()=>`cb-${Date.now()}-${crypto.getRandomValues(new Uint32Array(2)).join('-')}`;
 const invoke=(action,detail={})=>new Promise(resolve=>{
  if(!host||typeof host.dispatch!=='function'){resolve({ok:false,error:'Android host unavailable — UI preview only.'});return;}
  const id=callbackId(),timer=setTimeout(()=>{pending.delete(id);resolve({ok:false,error:'Native request timed out.'});},310000);
  pending.set(id,value=>{clearTimeout(timer);resolve(value);});
  try{host.dispatch(JSON.stringify({action,...detail}),id);}catch(error){clearTimeout(timer);pending.delete(id);resolve({ok:false,error:String(error)});}
 });
 Object.defineProperty(globalThis,'OracylNativeResult',{value:(id,json)=>{
  const done=pending.get(id);if(!done)return;pending.delete(id);
  try{done(JSON.parse(json));}catch(_){done({ok:false,error:'Android host returned invalid JSON.'});}
 }});
 const notify=msg=>{const t=document.getElementById('toast');if(t){t.textContent=msg;t.classList.add('visible');setTimeout(()=>t.classList.remove('visible'),2600);}else console.warn(msg);};
 const mk=(tag,cls,label)=>{const el=document.createElement(tag);el.className=cls;el.textContent=label;return el;};
 const activeChat=()=>sessionStorage.getItem('oracyl.chat.id')||'';
 const renderMessage=(role,text)=>{
  const desktop=document.getElementById('conversationAppend'),mobile=document.getElementById('mNewMessages');
  if(desktop){const row=mk('div',role==='operator'?'user-message':'assistant-message','');const body=mk('p',role==='operator'?'user-bubble':'oracyl-host-reply',text);row.appendChild(body);desktop.appendChild(row);}
  if(mobile){const row=mk('div',role==='operator'?'m-user-bubble':'m-answer',text);mobile.appendChild(row);}
 };
 const clearFixtures=()=>{
  document.querySelectorAll('#thread>.opening,#thread>.user-message,#thread>.assistant-message').forEach(node=>node.remove());
  const u=document.getElementById('mOriginalUser'),a=document.getElementById('mOriginalAnswer'),tools=document.getElementById('mReplyTools');
  if(u?.parentElement)u.parentElement.style.display='none';if(a)a.style.display='none';if(tools)tools.style.display='none';
 };
 const openChat=async id=>{sessionStorage.setItem('oracyl.chat.id',id);const history=await invoke('chat_history',{chat_id:id});
  document.getElementById('conversationAppend')?.replaceChildren();document.getElementById('mNewMessages')?.replaceChildren();
  if(history.ok)history.messages.forEach(row=>renderMessage(row.role,row.content));
  const draft=await invoke('draft_load',{chat_id:id});if(draft.ok){const d=document.getElementById('chatInput'),m=document.getElementById('mInput');if(d)d.value=draft.text;if(m)m.value=draft.text;}
 };
 const bind=(inputId,sendId,containerId)=>{
  const input=document.getElementById(inputId),button=document.getElementById(sendId),container=document.getElementById(containerId);
  if(!input||!button||!container)return;
  let busy=false;
  const submit=async e=>{const value=input.value.trim();if(!value||busy)return;e.preventDefault();e.stopImmediatePropagation();
   busy=true;button.setAttribute('aria-busy','true');renderMessage('operator',value);
   const response=await invoke('send',{text:value,chat_id:activeChat()});
   busy=false;button.removeAttribute('aria-busy');
   if(!response.ok){notify(response.error||'Inference unavailable');return;}
   if(response.chat_id)sessionStorage.setItem('oracyl.chat.id',response.chat_id);
   renderMessage('victor',response.text||'');input.value='';
  };
  button.addEventListener('click',submit,true);
  input.addEventListener('keydown',e=>{if(e.key==='Enter'&&!e.shiftKey)submit(e);},true);
  input.addEventListener('input',()=>{const id=activeChat();if(id)invoke('draft_save',{chat_id:id,text:input.value});});
 };
 bind('chatInput','voice','conversationAppend');bind('mInput','mVoice','mNewMessages');
 const newChat=async event=>{event?.preventDefault();event?.stopImmediatePropagation();const r=await invoke('chat_new');if(r.ok){await openChat(r.chat_id);notify('New chat');}else notify(r.error||'Unable to create chat');};
 ['newChat','mNewChat','mCreateTop'].forEach(id=>document.getElementById(id)?.addEventListener('click',newChat,true));
 ['attach','mAttach'].forEach(id=>document.getElementById(id)?.addEventListener('click',event=>{event.preventDefault();event.stopImmediatePropagation();invoke('knowledge_import').then(r=>notify(r.ok?'Knowledge stored with provenance':r.error));},true));
 document.getElementById('addTab')?.addEventListener('click',event=>{event.preventDefault();event.stopImmediatePropagation();invoke('browser_open',{url:'https://www.google.com'}).then(r=>{if(!r.ok)notify(r.error);});},true);
 const categories=['Chats','Research','Tasks','Apps','Tabs','Projects','Skills','Accounts'];
 document.querySelectorAll('.nav-btn[data-category]').forEach(btn=>btn.addEventListener('click',()=>{
  const kind=btn.dataset.category;if(kind==='Chats'||!categories.includes(kind))return;
  const title=document.getElementById('overlayTitle'),description=document.getElementById('overlayDescription'),pane=document.getElementById('overlay');
  if(title&&description&&pane){title.textContent=kind;description.textContent=`${kind}: loading native status…`;pane.classList.add('visible');pane.setAttribute('aria-hidden','false');}
  if(kind==='Tabs')invoke('browser_open',{}).then(r=>{if(description)description.textContent=r.ok?'Browser opened.':r.error;});
 }));
 Object.defineProperty(globalThis,'OracylHarness',{value:Object.freeze({invoke,categories})});
 if(host&&typeof host.dispatch==='function'){
  clearFixtures();
  invoke('chat_list').then(async r=>{if(!r.ok)return notify(r.error);if(!Array.isArray(r.chats))return notify('Native chat list was invalid');const desktop=document.getElementById('recentList'),mobile=document.getElementById('mRecent');
   desktop?.replaceChildren();mobile?.replaceChildren();
   r.chats.forEach(chat=>{const d=mk('button','item',chat.title);d.addEventListener('click',()=>openChat(chat.id));desktop?.appendChild(d);
    const m=mk('button','m-list-item',chat.title);m.addEventListener('click',()=>openChat(chat.id));mobile?.appendChild(m);});
   if(r.chats.length)await openChat(activeChat()||r.chats[0].id);else{const n=await invoke('chat_new');if(n.ok)await openChat(n.chat_id);}
  });
 }
})();
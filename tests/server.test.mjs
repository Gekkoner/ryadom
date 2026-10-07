import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync,readdirSync} from 'node:fs';
import {createApp} from '../src/server.js';
const db=new DatabaseSync(':memory:');db.exec('PRAGMA foreign_keys=ON');for(const f of readdirSync('drizzle').filter(f=>f.endsWith('.sql')))db.exec(readFileSync('drizzle/'+f,'utf8'));
const DB={prepare(q){return {bind(...args){const s=db.prepare(q);return {async first(){return s.get(...args)||null},async all(){return {results:s.all(...args)}},async run(){return s.run(...args)}}}}} ,async batch(qs){db.exec('BEGIN');try{const r=[];for(const q of qs)r.push(await q.run());db.exec('COMMIT');return r}catch(e){db.exec('ROLLBACK');throw e}}};
const blobs=new Map();const BUCKET={async put(id,stream){blobs.set(id,new Uint8Array(await new Response(stream).arrayBuffer()))},async delete(id){blobs.delete(id)},async get(id){const data=blobs.get(id);return data?{body:data,size:data.length}:null}};
const app=createApp('<h1>Рядом</h1>');let seq=1;
async function req(path,{method='GET',data,cookie,origin='https://test.invalid',form,ip='test'}={}){const headers={'cf-connecting-ip':ip};if(method!=='GET')headers.origin=origin;if(cookie)headers.cookie=cookie;if(data)headers['content-type']='application/json';const response=await app.fetch(new Request('https://test.invalid'+path,{method,headers,body:form|| (data?JSON.stringify(data):undefined)}),{DB,BUCKET},{});const type=response.headers.get('content-type'),body=type?.includes('json')?await response.json():await response.text();return {status:response.status,body,cookie:response.headers.get('set-cookie')?.split(';')[0],headers:response.headers}}
async function signup(handle){return req('/api/register',{method:'POST',data:{name:handle,handle,password:'safe-password-for-tests'},ip:'register'+seq++})}
test('Real-account lifecycle, persistence, memberships, files and recovery',async()=>{
const a=await signup('alice'),b=await signup('bob'),c=await signup('carol');assert.equal(a.status,201);assert.equal(b.status,201);assert.equal(c.status,201);assert.match(a.headers.get('set-cookie'),/HttpOnly; Secure; SameSite=Strict/);assert.equal(a.body.user.password,undefined);assert.equal((await req('/api/me',{cookie:a.cookie})).body.user.handle,'alice');assert.equal((await req('/api/me')).status,401);
assert.equal((await req('/api/register',{method:'POST',data:{name:'Duplicate',handle:'ALICE',password:'safe-password-for-tests'}})).status,409);
assert.equal((await req('/api/login',{method:'POST',data:{handle:'alice',password:'wrong-password'}})).status,401);
const login=await req('/api/login',{method:'POST',data:{handle:'alice',password:'safe-password-for-tests'}});assert.equal(login.status,200);
const chat=await req('/api/chats',{method:'POST',cookie:a.cookie,data:{handle:'bob'}});assert.equal(chat.status,200);const id=chat.body.chatId;
const duplicate=await req('/api/chats',{method:'POST',cookie:b.cookie,data:{handle:'alice'}});assert.equal(duplicate.body.chatId,id);
assert.equal((await req('/api/messages?chat='+id,{cookie:c.cookie})).status,403);
assert.equal((await req('/api/messages',{method:'POST',cookie:c.cookie,data:{chat:id,text:'intrusion',nonce:'bad'}})).status,403);
assert.equal((await req('/api/messages',{method:'POST',cookie:a.cookie,origin:'https://evil.invalid',data:{chat:id,text:'csrf',nonce:'csrf'}})).status,403);
assert.equal((await req('/api/messages',{method:'POST',cookie:a.cookie,data:{chat:id,text:'Привет <script>',nonce:'msg1'}})).status,201);
await req('/api/messages',{method:'POST',cookie:a.cookie,data:{chat:id,text:'Привет <script>',nonce:'msg1'}});
const read=await req('/api/messages?chat='+id,{cookie:b.cookie});assert.equal(read.body.messages.length,1);assert.equal(read.body.messages[0].body,'Привет <script>');assert.equal((await req('/api/chats',{cookie:b.cookie})).body.chats[0].peer_handle,'alice');
const form=new FormData();form.set('file',new File(['hello file'],'test.txt',{type:'text/plain'}));form.set('chat',id);const upload=await req('/api/upload',{method:'POST',cookie:a.cookie,form});assert.equal(upload.status,201);const fid=upload.body.fileId;assert.equal((await req('/api/files/'+fid,{cookie:b.cookie})).body,'hello file');assert.equal((await req('/api/files/'+fid,{cookie:c.cookie})).status,403);assert.equal((await req('/api/files/'+fid)).status,401);assert.equal((await req('/api/messages?chat='+id,{cookie:b.cookie})).body.messages.length,2);
const av=new FormData();av.set('file',new File(['fake-raster'],'avatar.png',{type:'image/png'}));av.set('kind','avatar');const avatar=await req('/api/upload',{method:'POST',cookie:a.cookie,form:av});assert.equal(avatar.status,201);assert.equal((await req('/api/me',{cookie:a.cookie})).body.user.avatar,avatar.body.fileId);
const profile=await req('/api/profile',{method:'PATCH',cookie:a.cookie,data:{name:'Алиса',bio:'Привет',accent:'#85d5ff'}});assert.equal(profile.body.user.name,'Алиса');assert.equal((await req('/api/users?q=ali',{cookie:b.cookie})).body.users[0].name,'Алиса');
const group=await req('/api/chats',{method:'POST',cookie:a.cookie,data:{kind:'group',title:'Свои',handles:['bob','carol']}});assert.equal(group.status,201);assert.equal((await req('/api/chat-profile?chat='+group.body.chatId,{cookie:c.cookie})).body.users.length,3);
const reset=await req('/api/recover',{method:'POST',data:{handle:'alice',password:'new-safe-password',recovery:a.body.recovery}});assert.equal(reset.status,200);assert.equal((await req('/api/me',{cookie:a.cookie})).status,401);assert.equal((await req('/api/me',{cookie:login.cookie})).status,401);assert.equal((await req('/api/me',{cookie:reset.cookie})).status,200);
assert.equal((await req('/api/recover',{method:'POST',data:{handle:'alice',password:'new-safe-password',recovery:a.body.recovery}})).status,400);
await req('/api/logout',{method:'POST',cookie:reset.cookie,data:{}});assert.equal((await req('/api/me',{cookie:reset.cookie})).status,401);
const row=db.prepare('SELECT password,salt FROM users WHERE handle=?').get('alice');assert.notEqual(row.password,'new-safe-password');assert.equal(row.password.length,64);
});
test('login rate limiting and anonymous HTML',async()=>{for(let i=0;i<12;i++)await req('/api/login',{method:'POST',ip:'limit-ip',data:{handle:'missing',password:'safe-password-for-tests'}});assert.equal((await req('/api/login',{method:'POST',ip:'limit-ip',data:{handle:'missing',password:'safe-password-for-tests'}})).status,429);assert.equal((await req('/')).status,200)});

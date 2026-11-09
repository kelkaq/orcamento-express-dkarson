import {test} from 'node:test';
import assert from 'node:assert/strict';
import {b64url,fromB64url,token,sha256,normalizeEmail,isRecoveryKey,subscriptionStatus,
 validateBackup,validateQuote,parseRtdn,esc,parseJson} from './lib.mjs';

test('base64url and session digests produce unique secrets',async()=>{
 const raw=new Uint8Array([0,1,255,253,254,102]);
 assert.deepEqual(fromB64url(b64url(raw)),raw);
 assert.notEqual(token(),token());
 assert.equal((await sha256('token')).length,43);
});
test('identity and imported backups reject malformed inputs',()=>{
 assert.equal(normalizeEmail(' User@Example.com '),'user@example.com');
 assert.throws(()=>normalizeEmail('not an email'));
 const valid={app:'Orçamento Express',version:4,quotes:[],items:[],clients:[],products:[]};
 assert.deepEqual(validateBackup(JSON.stringify(valid)),valid);
 assert.throws(()=>validateBackup('{}'));
 assert.throws(()=>parseJson('{',100));
});
test('public quote snapshot validates costs and escapes output',()=>{
 const q=validateQuote({quoteNumber:'OR-01',clientName:'<test>',
 total:100,items:[{description:'Web',unit:'un',quantity:1,unitPrice:100}]});
 assert.equal(q.items.length,1);
 assert.throws(()=>validateQuote({quoteNumber:'X',total:-9,items:[]}));
 assert.equal(esc(q.clientName),'&lt;test&gt;');
});
test('subscription states preserve paid term and block holds',()=>{
 const exp=new Date(Date.now()+86400000).toISOString();
 const row=(s)=>({subscriptionState:s,lineItems:[{productId:'sku',expiryTime:exp}]});
 assert.equal(subscriptionStatus(row('SUBSCRIPTION_STATE_ACTIVE'),'sku').active,true);
 assert.equal(subscriptionStatus(row('SUBSCRIPTION_STATE_CANCELED'),'sku').active,true);
 assert.equal(subscriptionStatus(row('SUBSCRIPTION_STATE_ON_HOLD'),'sku').active,false);
 assert.equal(subscriptionStatus(row('SUBSCRIPTION_STATE_ACTIVE'),'other').active,false);
});
test('Pub/Sub envelope is scoped to app and product',()=>{
 const e=(pkg,sku)=>({message:{data:btoa(JSON.stringify({packageName:pkg,
   subscriptionNotification:{subscriptionId:sku,purchaseToken:'12345678901234567890'}}))}});
 assert.equal(parseRtdn(e('com.example.app','paid'),'com.example.app','paid').kind,'subscription');
 assert.equal(parseRtdn(e('another.app','paid'),'com.example.app','paid'),null);
 assert.throws(()=>parseRtdn({},'com.example.app','paid'));
});

test('recovery keys are strong 256-bit secrets, never short user passwords',()=>{
  const key=token();
  assert.equal(isRecoveryKey(key),true);
  assert.equal(key.length,43);
  assert.equal(isRecoveryKey('123456'),false);
  assert.equal(isRecoveryKey('password@example.com'),false);
});

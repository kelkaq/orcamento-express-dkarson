import {test} from 'node:test';
import assert from 'node:assert/strict';
import {assertBackup,assertQuoteForApproval,createCrypto,
  normalizeEmail,esc,subscriptionStatus,otpHash,safeEqualHex,googleIdentityClaims,
  parsePlayRtdnEnvelope} from './lib.mjs';

test('invalid backups cannot overwrite a user history',()=>{
  assert.throws(()=>assertBackup('{"app":"Other","version":4}'));
  assert.throws(()=>assertBackup('{}'));
  assert.equal(assertBackup(JSON.stringify({app:'Orçamento Express',version:4,quotes:[],items:[],clients:[],products:[]})).quotes.length,0);
});
test('approval link rejects forged malformed prices',()=>{
  assert.throws(()=>assertQuoteForApproval({quoteNumber:'x',total:-1,items:[]}));
  const data=assertQuoteForApproval({quoteNumber:'ORC1',clientName:'Ana',total:100,items:[
    {description:'Serviço',quantity:1,unitPrice:100,unit:'un'}
  ]});
  assert.equal(data.items.length,1);
});
test('AES256GCM encrypts and detects tampering',()=>{
  const crypto=createCrypto(Buffer.alloc(32,77).toString('base64'));
  const cipher=crypto.seal('cliente CPF privativo');
  assert.equal(crypto.open(cipher),'cliente CPF privativo');
  const bytes=Buffer.from(cipher,'base64');bytes[30]^=1;
  assert.throws(()=>crypto.open(bytes.toString('base64')));
});
test('one-time codes and email normalization',()=>{
  const email=normalizeEmail(' Ana@Exemplo.COM ');
  assert.equal(email,'ana@exemplo.com');
  assert.throws(()=>normalizeEmail('not email'));
  assert.equal(safeEqualHex(otpHash(email,'123456','secret'),
    otpHash(email,'123456','secret')),true);
  assert.equal(safeEqualHex(otpHash(email,'123456','secret'),
    otpHash(email,'123457','secret')),false);
});
test('HTML and Google status safety',()=>{
  assert.equal(esc('<script>'),'&lt;script&gt;');
  const future=new Date(Date.now()+86400000).toISOString();
  assert.equal(subscriptionStatus({subscriptionState:'SUBSCRIPTION_STATE_ON_HOLD',
    lineItems:[{productId:'sub',expiryTime:future}]},'sub').active,false);
});

test('Google ID requires email verification, subject, and canonical email',()=>{
  assert.deepEqual(googleIdentityClaims({email:' User@Gmail.COM ',
    email_verified:true,sub:'12345678abcde'}),{
    email:'user@gmail.com',subject:'12345678abcde'
  });
  assert.throws(()=>googleIdentityClaims({
    email:'user@gmail.com',email_verified:false,sub:'12345678abcde'}));
  assert.throws(()=>googleIdentityClaims({
    email:'user@gmail.com',email_verified:true,sub:''}));
});

test('Google Play notification parser accepts only the subscribed app and subscription',()=>{
  const sample = (packageName='com.dkarson.orcamentoexpress',subscriptionId='orcamento_express_pro_mensal') => ({
    message:{data:Buffer.from(JSON.stringify({packageName,subscriptionNotification:{
      subscriptionId,purchaseToken:'token_example_string_1234567890',notificationType:2
    }})).toString('base64')}
  });
  const id='orcamento_express_pro_mensal', pkg='com.dkarson.orcamentoexpress';
  assert.deepEqual(parsePlayRtdnEnvelope(sample(),pkg,id),{
    purchaseToken:'token_example_string_1234567890',type:2
  });
  assert.equal(parsePlayRtdnEnvelope(sample('other.app'),pkg,id),null);
  assert.equal(parsePlayRtdnEnvelope(sample(pkg,'other_sku'),pkg,id),null);
  assert.throws(()=>parsePlayRtdnEnvelope({message:{data:'abc'}},pkg,id));
});

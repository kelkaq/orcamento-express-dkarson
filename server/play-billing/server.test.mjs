import {test} from 'node:test';
import assert from 'node:assert/strict';
process.env.NODE_ENV='test';
process.env.PLAY_PACKAGE_NAME='com.dkarson.orcamentoexpress';
const {evaluateSubscription, server}=await import('./server.mjs');
const future=new Date(Date.now()+2*86_400_000).toISOString();
const past=new Date(Date.now()-86_400_000).toISOString();
const quote = (state,expiry)=>({
  subscriptionState:state,
  lineItems:[{productId:'orcamento_express_pro_mensal',expiryTime:expiry}]
});
test('active subscription grants paid entitlement',()=>{
  assert.equal(evaluateSubscription(quote('SUBSCRIPTION_STATE_ACTIVE',future)).active,true);
});
test('canceled remains active until paid term expires',()=>{
  assert.equal(evaluateSubscription(quote('SUBSCRIPTION_STATE_CANCELED',future)).active,true);
});
test('payment hold and expired cannot grant Pro',()=>{
  assert.equal(evaluateSubscription(quote('SUBSCRIPTION_STATE_ON_HOLD',future)).active,false);
  assert.equal(evaluateSubscription(quote('SUBSCRIPTION_STATE_ACTIVE',past)).active,false);
});
test('wrong product cannot grant entitlement',()=>{
  assert.equal(evaluateSubscription({subscriptionState:'SUBSCRIPTION_STATE_ACTIVE',
    lineItems:[{productId:'other_sku',expiryTime:future}]}).active,false);
});

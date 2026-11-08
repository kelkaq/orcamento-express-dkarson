import {b64url,bytes,subscriptionStatus} from './lib.mjs';

const packageId=Deno.env.get('PLAY_PACKAGE_NAME') || 'com.dkarson.orcamentoexpress';
const offlineSku='orcamento_express_pro_offline';
const subscriptionSku='orcamento_express_pro_mensal';
const apiRoot='https://androidpublisher.googleapis.com/androidpublisher/v3/applications/' +
  encodeURIComponent(packageId);
let cachedAccess: {token:string;until:number}|null=null;

async function getAccessToken():Promise<string> {
  if(cachedAccess && cachedAccess.until>Date.now()+60_000)return cachedAccess.token;
  const json=Deno.env.get('PLAY_SERVICE_ACCOUNT_JSON');
  if(!json)throw new Error('Acesso Google Play não configurado');
  const service=JSON.parse(json);
  if(!service.client_email || !service.private_key)
    throw new Error('Conta de serviço inválida');
  const cleaned=String(service.private_key).replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g,'');
  const pkcs8=Uint8Array.from(atob(cleaned),x=>x.charCodeAt(0));
  const privateKey=await crypto.subtle.importKey('pkcs8',pkcs8,
    {name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
  const now=Math.floor(Date.now()/1000);
  const header=b64url(bytes(JSON.stringify({alg:'RS256',typ:'JWT'})));
  const claims=b64url(bytes(JSON.stringify({
    iss:service.client_email,
    scope:'https://www.googleapis.com/auth/androidpublisher',
    aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3300
  })));
  const body=header+'.'+claims;
  const signature=b64url(await crypto.subtle.sign('RSASSA-PKCS1-v1_5',privateKey,bytes(body)));
  const response=await fetch('https://oauth2.googleapis.com/token',{
    method:'POST',headers:{'content-type':'application/x-www-form-urlencoded'},
    body:new URLSearchParams({
      grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion:body+'.'+signature
    })
  });
  if(!response.ok)throw new Error('Google Play não autorizou o servidor');
  const data=await response.json();
  if(typeof data.access_token!=='string')throw new Error('Token do Google inválido');
  cachedAccess={token:data.access_token,
    until:Date.now()+Math.min(3300,Number(data.expires_in)||3300)*1000};
  return cachedAccess.token;
}
async function googleApi(path:string,method='GET',body?:unknown):Promise<any> {
  const access=await getAccessToken();
  const response=await fetch(apiRoot+path,{
    method,headers:{Authorization:'Bearer '+access,
      'content-type':'application/json'},
    body:body===undefined?undefined:JSON.stringify(body)
  });
  if(!response.ok){
    if(response.status===404)return {notFound:true};
    throw new Error('A verificação Google Play está indisponível ('+response.status+')');
  }
  const txt=await response.text();
  return txt?JSON.parse(txt):{};
}

/** Nunca informa compra ativa a partir de parâmetros enviados pelo celular. */
export async function verifyPurchase(
  purchaseToken:string,type:'one_time'|'subscription'
):Promise<{active:boolean;expiresAtMillis?:number;lifetime:boolean;productId:string}> {
  if(typeof purchaseToken!=='string' || purchaseToken.length<16 || purchaseToken.length>4096)
    throw new Error('Token de compra inválido');
  if(type==='one_time'){
    const path='/purchases/products/'+encodeURIComponent(offlineSku)+
      '/tokens/'+encodeURIComponent(purchaseToken);
    const purchase=await googleApi(path);
    const active=!purchase.notFound && Number(purchase.purchaseState)===0 &&
      Number(purchase.consumptionState||0)!==1;
    if(active && Number(purchase.acknowledgementState)===0)
      await googleApi(path+':acknowledge','POST',{});
    return {active,lifetime:active,productId:offlineSku};
  }
  const purchase=await googleApi('/purchases/subscriptionsv2/tokens/'+
    encodeURIComponent(purchaseToken));
  if(purchase.notFound)
    return {active:false,expiresAtMillis:0,lifetime:false,productId:subscriptionSku};
  const state=subscriptionStatus(purchase,subscriptionSku);
  if(state.active && purchase.acknowledgementState==='ACKNOWLEDGEMENT_STATE_PENDING'){
    await googleApi('/purchases/subscriptions/'+encodeURIComponent(subscriptionSku)+
      '/tokens/'+encodeURIComponent(purchaseToken)+':acknowledge','POST',{});
  }
  return {...state,lifetime:false,productId:subscriptionSku};
}
export const productInfo=Object.freeze({packageId,offlineSku,subscriptionSku});

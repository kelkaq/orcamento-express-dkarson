import {createServer} from 'node:http';
import {GoogleAuth} from 'google-auth-library';

const packageName = process.env.PLAY_PACKAGE_NAME || '';
const productId = process.env.PLAY_PRODUCT_ID || 'orcamento_express_pro_mensal';
const oneTimeProductId = process.env.PLAY_ONE_TIME_PRODUCT_ID || 'orcamento_express_pro_offline';
const port = Number(process.env.PORT || 8088);
if (!/^com\.[a-zA-Z0-9_.]+$/.test(packageName)) {
  throw new Error('Configure PLAY_PACKAGE_NAME no ambiente.');
}
const auth = new GoogleAuth({scopes:['https://www.googleapis.com/auth/androidpublisher']});
const recent = new Map();
const interval = setInterval(() => {
  const now = Date.now();
  for (const [ip,data] of recent) if (now - data.since > 120_000) recent.delete(ip);
}, 120_000);
interval.unref();

async function getToken() {
  const accessToken = await auth.getAccessToken();
  if (!accessToken) throw new Error('Conta de serviço não autorizada.');
  return accessToken;
}
async function googleGet(token) {
  const key = await getToken();
  const url = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/' +
    encodeURIComponent(packageName) + '/purchases/subscriptionsv2/tokens/' + encodeURIComponent(token);
  const response = await fetch(url,{headers:{Authorization:'Bearer '+key}});
  if(!response.ok) throw new Error('Verificação Google Play falhou.');
  return response.json();
}
async function googleGetOneTime(token) {
  const key = await getToken();
  const url = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/' +
    encodeURIComponent(packageName) + '/purchases/products/' +
    encodeURIComponent(oneTimeProductId) + '/tokens/' + encodeURIComponent(token);
  const response = await fetch(url, {headers:{Authorization:'Bearer '+key}});
  if(!response.ok) throw new Error('Compra única não confirmada na Google Play.');
  return response.json();
}
async function acknowledgeOneTime(token) {
  const key = await getToken();
  const url = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/' +
    encodeURIComponent(packageName) + '/purchases/products/' +
    encodeURIComponent(oneTimeProductId) + '/tokens/' + encodeURIComponent(token) + ':acknowledge';
  const response = await fetch(url,{
    method:'POST', headers:{Authorization:'Bearer '+key,'Content-Type':'application/json'},
    body:'{}'
  });
  if(!response.ok) throw new Error('Falha ao reconhecer compra única.');
}
async function acknowledge(token) {
  const key = await getToken();
  const url = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/' +
    encodeURIComponent(packageName) + '/purchases/subscriptions/' +
    encodeURIComponent(productId) + '/tokens/' + encodeURIComponent(token) + ':acknowledge';
  const response = await fetch(url,{method:'POST',headers:{
    Authorization:'Bearer '+key,'Content-Type':'application/json'
  },body:'{}'});
  if (!response.ok) throw new Error('Falha ao reconhecer compra.');
}

export function evaluateSubscription(data, now=Date.now()) {
  const allowed = new Set([
    'SUBSCRIPTION_STATE_ACTIVE',
    'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
    'SUBSCRIPTION_STATE_CANCELED' // Direito de uso até o fim já pago.
  ]);
  const items=(data.lineItems||[]).filter(i=>i.productId===productId);
  const expires=Math.max(0,...items.map(i=>Date.parse(i.expiryTime)||0));
  return {
    active: allowed.has(data.subscriptionState) && expires>now,
    expiresAtMillis: expires
  };
}

const server=createServer(async(req,res)=>{
  res.setHeader('Cache-Control','no-store');
  res.setHeader('X-Content-Type-Options','nosniff');
  if(req.method==='GET' && req.url==='/health') {
    res.writeHead(200,{'Content-Type':'application/json'});
    return res.end(JSON.stringify({ok:true}));
  }
  if(req.method!=='POST' || req.url!=='/v1/play/verify') {
    res.writeHead(404);return res.end();
  }
  const ip=req.socket.remoteAddress || 'unknown';
  const now=Date.now();
  let entry=recent.get(ip);
  if(!entry||now-entry.since>60_000) entry={since:now,count:0};
  entry.count++;
  recent.set(ip,entry);
  if(entry.count>30) {res.writeHead(429);return res.end();}
  try {
    let raw='';
    for await(const chunk of req) {
      raw += chunk.toString('utf8');
      if(raw.length>8192) {res.writeHead(413);return res.end();}
    }
    const {purchaseToken,type='subscription'}=JSON.parse(raw);
    if(typeof purchaseToken!=='string'||purchaseToken.length<16||purchaseToken.length>4096 ||
       !['subscription','one_time'].includes(type)) {
      res.writeHead(400);return res.end();
    }
    let result;
    if(type==='one_time') {
      const purchase=await googleGetOneTime(purchaseToken);
      const active=purchase.purchaseState===0 && purchase.consumptionState!==1;
      if(active && purchase.acknowledgementState===0) await acknowledgeOneTime(purchaseToken);
      result={active,lifetime:active,productId:oneTimeProductId};
    } else {
      const data=await googleGet(purchaseToken);
      const subscription=evaluateSubscription(data);
      if(subscription.active && data.acknowledgementState==='ACKNOWLEDGEMENT_STATE_PENDING') {
        await acknowledge(purchaseToken);
      }
      result={...subscription,lifetime:false,productId};
    }
    res.writeHead(200,{'Content-Type':'application/json'});
    return res.end(JSON.stringify(result));
  } catch (error) {
    // Nunca registrar token de compra nem credenciais em logs.
    console.error('Falha na verificação:',error?.message || 'erro desconhecido');
    res.writeHead(503,{'Content-Type':'application/json'});
    return res.end(JSON.stringify({active:false,error:'verification_unavailable'}));
  }
});
if(process.env.NODE_ENV!=='test')server.listen(port,'127.0.0.1',()=>console.log('Verificador Play ativo em localhost:'+port));
export {server};

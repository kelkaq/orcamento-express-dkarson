import {createCipheriv,createDecipheriv,createHmac,randomBytes,timingSafeEqual} from 'node:crypto';

export const ALLOWED_STATES=new Set([
  'SUBSCRIPTION_STATE_ACTIVE','SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
  'SUBSCRIPTION_STATE_CANCELED'
]);

/** Decodifica apenas o envelope Pub/Sub. Estado de assinatura é sempre consultado no Google Play. */
export function parsePlayRtdnEnvelope(envelope, packageName, subscriptionId) {
  if(!envelope || typeof envelope?.message?.data!=='string')
    throw Object.assign(new Error('Envelope Pub/Sub inválido'),{status:400});
  let data;
  try {
    const text=Buffer.from(envelope.message.data,'base64').toString('utf8');
    data=JSON.parse(text);
  } catch {
    throw Object.assign(new Error('Notificação inválida'),{status:400});
  }
  if(data?.packageName!==packageName) return null;
  const subscription=data.subscriptionNotification;
  if(!subscription || subscription.subscriptionId!==subscriptionId) return null;
  const purchaseToken=subscription.purchaseToken;
  if(typeof purchaseToken!=='string'||purchaseToken.length<16||purchaseToken.length>4096)
    throw Object.assign(new Error('Token inválido'),{status:400});
  return {purchaseToken,type:Number(subscription.notificationType)||0};
}

export function normalizeEmail(value) {
  if(typeof value!=='string') throw new Error('E-mail inválido');
  const email=value.trim().toLowerCase();
  if(email.length>254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
    throw new Error('E-mail inválido');
  return email;
}
/** O ID do Google (sub) é estável; email não é uma chave de identidade. */
export function googleIdentityClaims(payload) {
  if(!payload || !payload.email_verified || typeof payload.sub!=='string' ||
      payload.sub.length<8 || payload.sub.length>255 || !payload.email)
    throw Object.assign(new Error('Identidade Google não verificada'),{status:401});
  return {subject:payload.sub,email:normalizeEmail(payload.email)};
}

export function subscriptionStatus(data,productId,now=Date.now()) {
  const items=(data.lineItems||[]).filter(x=>x.productId===productId);
  const expires=Math.max(0,...items.map(x=>Date.parse(x.expiryTime)||0));
  return {
    active:ALLOWED_STATES.has(data.subscriptionState)&&expires>now,
    expiresAtMillis:expires
  };
}
export function assertBackup(raw) {
  if(typeof raw!=='string'||Buffer.byteLength(raw,'utf8')>6_000_000)
    throw new Error('Backup maior que 6 MB');
  let root;
  try{root=JSON.parse(raw);}catch{throw new Error('Backup não é JSON válido');}
  if(root?.app!=='Orçamento Express'||!Number.isInteger(root.version)||
      root.version<3||root.version>5) throw new Error('Formato de backup incompatível');
  if(!Array.isArray(root.quotes)||!Array.isArray(root.items)||
     !Array.isArray(root.clients)||!Array.isArray(root.products))
    throw new Error('Backup incompleto');
  if(root.quotes.length>20_000 || root.items.length>150_000)
    throw new Error('Backup supera limite de registros');
  return root;
}
export function assertQuoteForApproval(data) {
  if(!data || typeof data!=='object') throw new Error('Orçamento inválido');
  const quoteNumber=String(data.quoteNumber||'').trim();
  const clientName=String(data.clientName||'').trim();
  if(!/^.{1,100}$/.test(quoteNumber)||clientName.length>160)
    throw new Error('Número do orçamento ou cliente inválido');
  const total=Number(data.total);
  if(!Number.isFinite(total)||total<0||total>1_000_000_000)
    throw new Error('Valor inválido');
  if(!Array.isArray(data.items)||data.items.length===0||data.items.length>150)
    throw new Error('Itens inválidos');
  const items=data.items.map(item=>{
    const description=String(item.description||'').trim();
    const quantity=Number(item.quantity),unitPrice=Number(item.unitPrice);
    const unit=String(item.unit||'un').slice(0,20);
    if(!description||description.length>200||!Number.isFinite(quantity)||
       quantity<=0||!Number.isFinite(unitPrice)||unitPrice<0)
      throw new Error('Item inválido');
    return {description,quantity,unitPrice,unit};
  });
  return {quoteNumber,clientName,total,items};
}
export function esc(text) {
  return String(text??'').replace(/[&<>"']/g,
    m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
}
export function createCrypto(keyBase64) {
  const key=Buffer.from(keyBase64||'','base64');
  if(key.length!==32) throw new Error('BACKUP_ENCRYPTION_KEY_BASE64 precisa de 32 bytes');
  return {
    seal(plain) {
      const iv=randomBytes(12), cipher=createCipheriv('aes-256-gcm',key,iv);
      const encrypted=Buffer.concat([cipher.update(String(plain),'utf8'),cipher.final()]);
      return Buffer.concat([iv,cipher.getAuthTag(),encrypted]).toString('base64');
    },
    open(encoded) {
      const bytes=Buffer.from(encoded,'base64');
      if(bytes.length<29) throw new Error('Conteúdo criptografado inválido');
      const decipher=createDecipheriv('aes-256-gcm',key,bytes.subarray(0,12));
      decipher.setAuthTag(bytes.subarray(12,28));
      return Buffer.concat([decipher.update(bytes.subarray(28)),decipher.final()]).toString('utf8');
    }
  };
}
export function hashSecret(value) {
  return createHmac('sha256','dkarson-token-hash-v1').update(value).digest('hex');
}
export function otpHash(email,code,secret) {
  return createHmac('sha256',secret).update(email+'|'+code).digest('hex');
}
export function safeEqualHex(first,second) {
  const a=Buffer.from(String(first),'hex'),b=Buffer.from(String(second),'hex');
  return a.length===b.length && a.length>0 && timingSafeEqual(a,b);
}
export function token() {return randomBytes(32).toString('base64url');}

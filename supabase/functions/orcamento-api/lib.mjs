/**
 * Pure helpers shared by the Supabase Edge Function and Node tests.
 * No service key, JWT, or user information is exposed to Android.
 */
const encoder = new TextEncoder();
export const bytes = (value) => encoder.encode(String(value));
export function b64url(buffer) {
  const raw = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  let base = '';
  for (let i = 0; i < raw.length; i += 0x6000) {
    base += String.fromCharCode(...raw.subarray(i,i+0x6000));
  }
  return btoa(base).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
}
export function fromB64url(input) {
  if(typeof input !== 'string' || !/^[\w-]+$/.test(input))
    throw new Error('Base64 inválido');
  const binary=atob(input.replace(/-/g,'+').replace(/_/g,'/'));
  return Uint8Array.from(binary, c => c.charCodeAt(0));
}
export function token() { return b64url(crypto.getRandomValues(new Uint8Array(32))); }
export async function sha256(value) {
  return b64url(await crypto.subtle.digest('SHA-256', bytes(value)));
}
/** Identidade sem e-mail: não use uma senha curta como código de recuperação. */
export function isRecoveryKey(value) {
  return typeof value==='string' && /^[A-Za-z0-9_-]{43}$/.test(value);
}
export function normalizeEmail(value) {
  if(typeof value!=='string')throw new Error('E-mail inválido');
  const email=value.trim().toLowerCase();
  if(email.length>254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
    throw new Error('E-mail inválido');
  return email;
}
export function subscriptionStatus(data, productId, now=Date.now()) {
  const allowed = new Set(['SUBSCRIPTION_STATE_ACTIVE',
    'SUBSCRIPTION_STATE_IN_GRACE_PERIOD','SUBSCRIPTION_STATE_CANCELED']);
  const items=(Array.isArray(data?.lineItems)?data.lineItems:[])
    .filter(x=>x.productId===productId);
  const expires=Math.max(0,...items.map(x=>Date.parse(x.expiryTime)||0));
  return {active:allowed.has(data?.subscriptionState)&&expires>now,expiresAtMillis:expires};
}
export function validateBackup(raw) {
  if(typeof raw!=='string' || bytes(raw).length>6_000_000)
    throw new Error('Backup deve ter no máximo 6 MB');
  let data;
  try{data=JSON.parse(raw);}catch{throw new Error('JSON de backup inválido');}
  if(data?.app!=='Orçamento Express' || !Number.isInteger(data.version) ||
      data.version<3 || data.version>5) throw new Error('Formato incompatível');
  for(const name of ['quotes','items','clients','products'])
    if(!Array.isArray(data[name]))throw new Error('Backup incompleto');
  if(data.quotes.length>20_000 || data.items.length>150_000 ||
     data.clients.length>50_000 || data.products.length>100_000)
    throw new Error('Limite de registros excedido');
  return data;
}
export function validateQuote(raw) {
  if(!raw || typeof raw!=='object')throw new Error('Orçamento inválido');
  const quoteNumber=String(raw.quoteNumber||'').trim().slice(0,101);
  const clientName=String(raw.clientName||'').trim().slice(0,161);
  const total=Number(raw.total);
  if(!quoteNumber || quoteNumber.length>100 || clientName.length>160 ||
     !Number.isFinite(total) || total<0 || total>1_000_000_000)
    throw new Error('Informações de orçamento inválidas');
  if(!Array.isArray(raw.items) || raw.items.length===0 || raw.items.length>150)
    throw new Error('Itens de orçamento inválidos');
  const items=raw.items.map(x=>{
    const description=String(x?.description||'').trim();
    const quantity=Number(x?.quantity), unitPrice=Number(x?.unitPrice);
    const unit=String(x?.unit||'un');
    if(!description || description.length>200 || !Number.isFinite(quantity) ||
      quantity<=0 || quantity>1e8 || !Number.isFinite(unitPrice) ||
      unitPrice<0 || unitPrice>1e9 || unit.length>20)
      throw new Error('Item inválido');
    return {description,quantity,unitPrice,unit};
  });
  return {quoteNumber,clientName,total,items};
}
export function parseRtdn(envelope, packageName, subscriptionId) {
  let data;
  try {
    if(typeof envelope?.message?.data!=='string')throw new Error('missing');
    data=JSON.parse(new TextDecoder().decode(fromB64url(envelope.message.data.replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,''))));
  }catch { throw new Error('Notificação inválida');}
  if(data.packageName!==packageName)return null;
  if(data.subscriptionNotification?.subscriptionId===subscriptionId) {
    const purchaseToken=data.subscriptionNotification.purchaseToken;
    if(typeof purchaseToken!=='string'||purchaseToken.length<16||purchaseToken.length>4096)
      throw new Error('Token de compra inválido');
    return {kind:'subscription',purchaseToken};
  }
  if(data.oneTimeProductNotification)return {kind:'one_time'};
  return null;
}
export function esc(value) {
  return String(value??'').replace(/[&<>"']/g, s => ({
    '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
  })[s]);
}
export function parseJson(raw, maxBytes) {
  if(typeof raw!=='string'||bytes(raw).length>maxBytes)throw new Error('Corpo muito grande');
  try{return JSON.parse(raw);}catch{throw new Error('JSON inválido');}
}

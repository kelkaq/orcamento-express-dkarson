/**
 * API comercial única para Orçamento Express, hospedável em *.supabase.co.
 * Possui endpoints compatíveis com o Android existente.
 * Rotas públicas: saúde, login, política, link de aceite e webhook autenticado.
 * Todas as rotas de dados verificam sessões aleatórias armazenadas como SHA-256.
 */
import {createClient} from 'npm:@supabase/supabase-js@2';
import {createRemoteJWKSet,jwtVerify} from 'npm:jose@6';
import {normalizeEmail,token,sha256,subscriptionStatus,
  validateBackup,validateQuote,parseRtdn,esc} from './lib.mjs';
import {seal,open} from './crypto.ts';
import {verifyPurchase,productInfo} from './play.ts';
import {webHtml,webJs,webCss} from './web.mjs';

const host=Deno.env.get('SUPABASE_URL')||'';
const secret=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')||'';
const anon=Deno.env.get('SUPABASE_ANON_KEY')||'';
const publicBase=(Deno.env.get('PUBLIC_EDGE_BASE_URL')||'').replace(/\/$/,'');
if(!host.startsWith('https://')||!secret||!anon||!publicBase.startsWith('https://'))
  throw new Error('Supabase URL e chaves de serviço não configuradas');
const svc=createClient(host,secret,{auth:{persistSession:false,autoRefreshToken:false}});
const auth=createClient(host,anon,{auth:{persistSession:false,autoRefreshToken:false}});
const googleKeys=createRemoteJWKSet(new URL('https://www.googleapis.com/oauth2/v3/certs'));

class ApiError extends Error {
  constructor(message:string,public status=400){super(message);}
}
const fail=(msg:string,status=400):never=>{throw new ApiError(msg,status);};
function json(res:unknown,status=200):Response {
  return new Response(JSON.stringify(res),{status,headers:{
    'content-type':'application/json; charset=utf-8','cache-control':'no-store',
    'x-content-type-options':'nosniff','referrer-policy':'no-referrer'
  }});
}
function html(value:string,status=200):Response {
  return new Response(value,{status,headers:{
    'content-type':'text/html; charset=utf-8','cache-control':'no-store',
    'x-content-type-options':'nosniff','referrer-policy':'no-referrer',
    'x-robots-tag':'noindex, nofollow','content-security-policy':
     "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'"
  }});
}
async function body(req:Request,max=6500000):Promise<any> {
  const size=Number(req.headers.get('content-length')||0);
  if(size>max)fail('Arquivo muito grande',413);
  const input=await req.text();
  if(new TextEncoder().encode(input).length>max)fail('Arquivo muito grande',413);
  try{return JSON.parse(input);}catch{fail('JSON inválido');}
}
function hostIp(req:Request):string {
  // O gateway Supabase preenche o endereço remoto; limite por e-mail também se aplica.
  return (req.headers.get('cf-connecting-ip')||req.headers.get('x-real-ip')||
    req.headers.get('x-forwarded-for')?.split(',')[0]||'unknown').slice(0,80);
}
async function limit(key:string,max:number,seconds:number) {
  const {data,error}=await svc.rpc('oe_check_rate_limit',{
    p_bucket:key,p_max:max,p_seconds:seconds
  });
  if(error)fail('Controle de acesso indisponível',503);
  if(data!==true)fail('Muitas tentativas. Tente novamente mais tarde.',429);
}
function dataOrThrow<T>(data:T,error:any):T {
  if(error) {
    if(error.code==='23505')fail('Conflito de conta ou compra existente',409);
    fail('Serviço de dados indisponível',503);
  }
  return data;
}
async function getUser(req:Request):Promise<any> {
  const bearer=req.headers.get('authorization')||'';
  if(!bearer.startsWith('Bearer '))fail('Faça login',401);
  const given=bearer.slice(7).trim();
  if(given.length<32||given.length>200)fail('Sessão inválida',401);
  const hashed=await sha256(given);
  const result=await svc.from('oe_sessions').select('user_id,expires_at')
    .eq('token_hash',hashed).maybeSingle();
  const session=dataOrThrow(result.data,result.error);
  if(!session)throw new ApiError('Sessão expirada',401);
  if(Date.parse(session.expires_at)<=Date.now())fail('Sessão expirada',401);
  const u=await svc.from('oe_users').select('id,email,auth_uid,google_sub')
    .eq('id',session.user_id).maybeSingle();
  const user=dataOrThrow(u.data,u.error);
  if(!user)fail('Conta inexistente',401);
  return user;
}
async function createSession(user:any):Promise<string> {
  const value=token();
  const result=await svc.from('oe_sessions').insert({
    user_id:user.id,token_hash:await sha256(value),
    expires_at:new Date(Date.now()+30*86400000).toISOString()
  });
  dataOrThrow(null,result.error);
  return value;
}
async function ensureEmailUser(email:string,uid:string):Promise<any> {
  const lookup=await svc.from('oe_users').select('id,email,auth_uid')
    .eq('email',email).maybeSingle();
  let user=dataOrThrow(lookup.data,lookup.error);
  if(user) {
    if(!user.auth_uid) {
      const updated=await svc.from('oe_users').update({auth_uid:uid})
        .eq('id',user.id).is('auth_uid',null).select().maybeSingle();
      user=dataOrThrow(updated.data,updated.error)||user;
    } else if(user.auth_uid!==uid) {
      fail('A conta está ligada a outra identidade de acesso',409);
    }
    return user;
  }
  const result=await svc.from('oe_users').insert({email,auth_uid:uid})
    .select().single();
  return dataOrThrow(result.data,result.error);
}
function confirmedGoogleUser(user:any):{email:string;uid:string;googleSub:string} {
  if(!user || !user.email_confirmed_at)fail('E-mail Google não verificado',401);
  const identity=(user.identities||[]).find((x:any)=>x.provider==='google');
  const googleSub=String(identity?.identity_data?.sub||identity?.id||'');
  if(googleSub.length<8||googleSub.length>255)fail('Identidade Google não localizada',401);
  return {email:normalizeEmail(user.email),uid:String(user.id),googleSub};
}
async function ensureGoogleUser(google:{email:string;uid:string;googleSub:string}):Promise<any> {
  const bySub=await svc.from('oe_users').select('id,email,google_sub,auth_uid')
    .eq('google_sub',google.googleSub).maybeSingle();
  const linked=dataOrThrow(bySub.data,bySub.error);
  if(linked) {
    if(linked.email!==google.email) {
      fail('Sua conta Google teve o e-mail alterado. Contate o suporte para confirmar identidade.',409);
    }
    return linked;
  }
  const byEmail=await svc.from('oe_users').select('id').eq('email',google.email).maybeSingle();
  if(dataOrThrow(byEmail.data,byEmail.error))
    fail('Esta conta já existe. Entre por código de e-mail e vincule seu Google.',409);
  const result=await svc.from('oe_users').insert({
    email:google.email,auth_uid:google.uid,google_sub:google.googleSub
  }).select().single();
  return dataOrThrow(result.data,result.error);
}
async function requireOnline(user:any):Promise<void> {
  const row=await svc.from('oe_subscriptions').select('*').eq('user_id',user.id).maybeSingle();
  const sub=dataOrThrow(row.data,row.error);
  if(!sub)fail('Assinatura Pro Online não vinculada',402);
  const now=Date.now();
  const expires=Date.parse(sub.expires_at||'');
  const last=Date.parse(sub.checked_at||'');
  if(expires>now && last>0 && now-last<5*60_000)return;
  const verified=await verifyPurchase(await open(sub.purchase_token_encrypted),'subscription');
  const update=await svc.from('oe_subscriptions').update({
    expires_at:verified.active?new Date(verified.expiresAtMillis||0).toISOString():null,
    checked_at:new Date().toISOString()
  }).eq('user_id',user.id).eq('purchase_token_hash',sub.purchase_token_hash);
  dataOrThrow(null,update.error);
  if(!verified.active)fail('Assinatura Pro Online inativa ou expirada',402);
}
async function cloud(req:Request,path:string):Promise<Response>{
  if(req.method==='POST' && path==='/v1/cloud/request-code'){
    const input=await body(req,2000),email=normalizeEmail(input.email);
    await limit('otp:email:'+(await sha256(email)),3,300);
    await limit('otp:ip:'+(await sha256(hostIp(req))),15,300);
    const result=await auth.auth.signInWithOtp({
      email,options:{shouldCreateUser:true}
    });
    if(result.error)fail('Não foi possível enviar o código. Confira configuração de e-mail.',503);
    return json({sent:true});
  }
  if(req.method==='POST' && path==='/v1/cloud/verify-code'){
    const input=await body(req,2000),email=normalizeEmail(input.email);
    const code=String(input.code||'');
    if(!/^\d{6}$/.test(code))fail('Informe os seis dígitos',400);
    await limit('otp:verify:'+(await sha256(email)),10,300);
    const result=await auth.auth.verifyOtp({email,token:code,type:'email'});
    if(result.error || !result.data.user?.email_confirmed_at)
      throw new ApiError('Código inválido ou expirado',401);
    const confirmedUser=result.data.user;
    if(!confirmedUser)throw new ApiError('Código inválido ou expirado',401);
    const user=await ensureEmailUser(email,String(confirmedUser.id));
    return json({token:await createSession(user),expiresInDays:30,email});
  }
  if(req.method==='POST' && path==='/v1/cloud/google-sign-in'){
    await limit('google:ip:'+(await sha256(hostIp(req))),20,60);
    const input=await body(req,10000);
    if(typeof input.idToken!=='string' || input.idToken.length>8192)
      fail('Identidade Google inválida');
    const result=await auth.auth.signInWithIdToken({
      provider:'google',token:input.idToken
    });
    if(result.error)fail('Não foi possível confirmar a conta Google',401);
    const identity=confirmedGoogleUser(result.data.user);
    const user=await ensureGoogleUser(identity);
    return json({token:await createSession(user),expiresInDays:30,email:user.email});
  }
  const user=await getUser(req);
  if(req.method==='POST' && path==='/v1/cloud/link-google'){
    await limit('link:google:'+user.id,6,60);
    const input=await body(req,10000);
    if(typeof input.idToken!=='string')fail('Identidade Google inválida');
    const result=await auth.auth.signInWithIdToken({
      provider:'google',token:input.idToken
    });
    if(result.error)fail('Conta Google não verificada',401);
    const google=confirmedGoogleUser(result.data.user);
    if(google.email!==user.email)fail('Entre com uma conta Google com o mesmo e-mail',409);
    const changed=await svc.from('oe_users')
      .update({google_sub:google.googleSub})
      .eq('id',user.id).or('google_sub.is.null,google_sub.eq.'+google.googleSub)
      .select('id').maybeSingle();
    if(!dataOrThrow(changed.data,changed.error))fail('Outra conta Google já vinculada',409);
    return json({linked:true,email:user.email});
  }
  if(req.method==='DELETE' && path==='/v1/cloud/session'){
    const hashed=await sha256((req.headers.get('authorization')||'').slice(7).trim());
    const result=await svc.from('oe_sessions').delete().eq('token_hash',hashed)
      .eq('user_id',user.id);
    dataOrThrow(null,result.error);
    return json({loggedOut:true});
  }
  if(req.method==='DELETE' && path==='/v1/cloud/account'){
    const input=await body(req,1000);
    if(input.confirm!=='EXCLUIR MINHA CONTA')fail('Confirme a exclusão',400);
    await limit('delete:'+user.id,3,3600);
    // Remove a identidade Auth quando for possível, além de todos os dados
    // em cascata. A assinatura na Google Play deve ser cancelada na loja.
    if(user.auth_uid){
      const done=await svc.auth.admin.deleteUser(user.auth_uid);
      if(done.error)fail('Não foi possível excluir a identidade de acesso',503);
    }
    const del=await svc.from('oe_users').delete().eq('id',user.id);
    dataOrThrow(null,del.error);
    return json({deleted:true});
  }
  if(req.method==='POST' && path==='/v1/cloud/link-subscription'){
    await limit('link:subscription:'+user.id,15,300);
    const input=await body(req,5000);
    const purchaseToken=input.purchaseToken;
    const verified=await verifyPurchase(purchaseToken,'subscription');
    if(!verified.active)return json({active:false},402);
    const record={
      user_id:user.id,purchase_token_hash:await sha256(purchaseToken),
      purchase_token_encrypted:await seal(purchaseToken),
      expires_at:new Date(verified.expiresAtMillis||0).toISOString(),
      checked_at:new Date().toISOString()
    };
    const result=await svc.from('oe_subscriptions').upsert(record,{onConflict:'user_id'});
    dataOrThrow(null,result.error);
    return json({active:true,expiresAtMillis:verified.expiresAtMillis});
  }
  await requireOnline(user);
  if(req.method==='GET' && path==='/v1/cloud/backup'){
    const result=await svc.from('oe_backups').select('revision,backup_encrypted')
      .eq('user_id',user.id).maybeSingle();
    const record=dataOrThrow(result.data,result.error);
    if(!record)return json({error:'no_cloud_backup'},404);
    return json({revision:record.revision,backup:await open(record.backup_encrypted)});
  }
  if(req.method==='POST' && path==='/v1/cloud/backup'){
    const input=await body(req,6_500_000);
    const backup=String(input.backup??'');
    validateBackup(backup);
    const expected=Number(input.expectedRevision);
    if(!Number.isSafeInteger(expected)||expected<0)fail('Revisão inválida');
    const initial=await svc.from('oe_backups').upsert({
      user_id:user.id,revision:0,backup_encrypted:await seal('{}')
    },{onConflict:'user_id',ignoreDuplicates:true});
    dataOrThrow(null,initial.error);
    const updated=await svc.from('oe_backups').update({
      revision:expected+1,backup_encrypted:await seal(backup),
      updated_at:new Date().toISOString()
    }).eq('user_id',user.id).eq('revision',expected)
      .select('revision').maybeSingle();
    const row=dataOrThrow(updated.data,updated.error);
    if(!row){
      const current=await svc.from('oe_backups').select('revision')
        .eq('user_id',user.id).maybeSingle();
      dataOrThrow(current.data,current.error);
      return json({error:'revision_conflict',currentRevision:current.data?.revision},409);
    }
    return json({revision:row.revision});
  }
  if(req.method==='POST' && path==='/v1/cloud/approval-links'){
    await limit('approval:'+user.id,40,60);
    const quote=validateQuote(await body(req,80000));
    const rawToken=token();
    const saved=await svc.from('oe_approval_links').insert({
      user_id:user.id,quote_number:quote.quoteNumber,token_hash:await sha256(rawToken),
      encrypted_snapshot:await seal(JSON.stringify(quote)),
      expires_at:new Date(Date.now()+14*86400000).toISOString()
    });
    dataOrThrow(null,saved.error);
    return json({url:publicBase+'/a/'+encodeURIComponent(rawToken),expiresInDays:14});
  }
  if(req.method==='GET' && path==='/v1/cloud/decisions'){
    const result=await svc.from('oe_approval_links')
      .select('quote_number,status,responded_at')
      .eq('user_id',user.id).in('status',['APPROVED','REJECTED'])
      .order('responded_at',{ascending:false}).limit(1000);
    const found=dataOrThrow(result.data,result.error);
    const distinct=new Map<string,any>();
    for(const row of found||[])if(!distinct.has(row.quote_number))
      distinct.set(row.quote_number,{
        quoteNumber:row.quote_number,status:row.status,respondedAt:row.responded_at
      });
    return json({decisions:[...distinct.values()]});
  }
  if(req.method==='GET' && path==='/v1/cloud/quotes'){
    const result=await svc.from('oe_backups').select('revision,backup_encrypted')
      .eq('user_id',user.id).maybeSingle();
    const found=dataOrThrow(result.data,result.error);
    if(!found)return json({quotes:[],revision:0});
    const backup=validateBackup(await open(found.backup_encrypted));
    return json({revision:found.revision,
      quotes:backup.quotes.map((q:any)=>({
        quoteNumber:q.quoteNumber,clientName:q.clientName,status:q.status,
        total:q.total,issueDate:q.issueDate
      }))});
  }
  return json({error:'not_found'},404);
}
function approvalHtml(quote:any,raw:string,status='PENDING') {
  const list=quote.items.map((x:any)=>'<li>'+esc(x.description)+' — '+
    esc(x.quantity)+' '+esc(x.unit)+' × R$ '+Number(x.unitPrice).toFixed(2)+'</li>').join('');
  const form=status==='PENDING'
    ? '<form method="POST" action="'+esc(publicBase)+'/a/'+encodeURIComponent(raw)+
      '/respond"><p>Confirme sua decisão sobre este orçamento.</p>'+
      '<button type="submit" name="decision" value="APPROVED">Aprovar orçamento</button> '+
      '<button type="submit" name="decision" value="REJECTED">Recusar orçamento</button></form>'
    : '<p><strong>Resposta registrada: '+(status==='APPROVED'?'APROVADO':'RECUSADO')+'</strong></p>';
  return '<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">'+
    '<meta name="viewport" content="width=device-width,initial-scale=1">'+
    '<title>Resposta ao orçamento — DKARSON</title><style>body{font:16px system-ui;max-width:680px;margin:30px auto;padding:0 18px;line-height:1.5}'+
    'button{padding:12px;border-radius:8px;border:1px solid #444;margin:5px}</style></head><body>'+
    '<h1>Orçamento '+esc(quote.quoteNumber)+'</h1><p>Cliente: '+esc(quote.clientName)+
    '</p><ul>'+list+'</ul><h2>Valor total: R$ '+Number(quote.total).toFixed(2)+
    '</h2>'+form+'<small>Este aceite eletrônico é um registro comercial, não substitui uma nota fiscal'+
    ' ou assinatura digital certificada.</small></body></html>';
}
async function approval(req:Request,path:string):Promise<Response>{
  const m=/^\/a\/([a-zA-Z0-9_-]{30,80})(\/respond)?$/.exec(path);
  if(!m)return html('<h1>Link não encontrado</h1>',404);
  const hashed=await sha256(m[1]);
  await limit('approval:view:'+hashed,60,60);
  const found=await svc.from('oe_approval_links').select('*')
    .eq('token_hash',hashed).maybeSingle();
  const row=dataOrThrow(found.data,found.error);
  if(!row||Date.parse(row.expires_at)<=Date.now())
    return html('<h1>Link expirado ou inexistente.</h1>',404);
  if(req.method==='GET' && !m[2])
    return html(approvalHtml(JSON.parse(await open(row.encrypted_snapshot)),m[1],row.status));
  if(req.method==='POST' && m[2]){
    const incoming=req.headers.get('origin');
    if(incoming && incoming!==new URL(publicBase).origin)
      return html('<h1>Origem inválida</h1>',403);
    const content=await req.text();
    if(content.length>1024)return html('<h1>Solicitação inválida</h1>',413);
    const decision=new URLSearchParams(content).get('decision');
    if(!['APPROVED','REJECTED'].includes(decision||''))
      return html('<h1>Resposta inválida</h1>',400);
    const update=await svc.from('oe_approval_links')
      .update({status:decision,responded_at:new Date().toISOString()})
      .eq('token_hash',hashed).eq('status','PENDING')
      .gt('expires_at',new Date().toISOString())
      .select('status').maybeSingle();
    dataOrThrow(update.data,update.error);
    const status=update.data?.status||row.status;
    return html(approvalHtml(JSON.parse(await open(row.encrypted_snapshot)),m[1],status));
  }
  return html('<h1>Método indisponível</h1>',405);
}
async function verifyPubsub(req:Request) {
  const audience=Deno.env.get('PUBSUB_PUSH_AUDIENCE');
  const email=Deno.env.get('PUBSUB_PUSH_SERVICE_ACCOUNT_EMAIL');
  if(!audience||!email)fail('Pub/Sub não configurado',503);
  const bearer=req.headers.get('authorization')||'';
  if(!bearer.startsWith('Bearer '))fail('Token de notificação ausente',401);
  const result=await jwtVerify(bearer.slice(7),googleKeys,{
    issuer:['https://accounts.google.com','accounts.google.com'],audience
  });
  if(result.payload.email!==email ||
    ![true,'true'].includes(result.payload.email_verified as any))
    fail('Serviço notificante não autorizado',403);
}
async function playRoute(req:Request,path:string):Promise<Response>{
  if(req.method==='POST' && path==='/v1/play/verify'){
    await limit('billing:ip:'+(await sha256(hostIp(req))),40,60);
    const input=await body(req,9000);
    if(!['subscription','one_time'].includes(input.type))fail('Tipo de compra inválido');
    const verified=await verifyPurchase(input.purchaseToken,input.type);
    return json(verified);
  }
  if(req.method==='POST' && path==='/v1/play/rtdn'){
    await verifyPubsub(req);
    const notice=parseRtdn(await body(req,15000),
      productInfo.packageId,productInfo.subscriptionSku);
    if(!notice)return json({ok:true,ignored:true});
    if(notice.kind==='one_time')
      return json({ok:true,note:'Compras únicas reconferidas pela Google Play no aplicativo'});
    const hashed=await sha256(notice.purchaseToken!);
    const result=await svc.from('oe_subscriptions')
      .select('user_id').eq('purchase_token_hash',hashed).maybeSingle();
    const owner=dataOrThrow(result.data,result.error);
    if(!owner)return json({ok:true,unlinked:true});
    const verify=await verifyPurchase(notice.purchaseToken!,'subscription');
    const updated=await svc.from('oe_subscriptions').update({
      expires_at:verify.active?new Date(verify.expiresAtMillis||0).toISOString():null,
      checked_at:new Date().toISOString()
    }).eq('user_id',owner.user_id).eq('purchase_token_hash',hashed);
    dataOrThrow(null,updated.error);
    return json({ok:true,active:verify.active});
  }
  return json({error:'not_found'},404);
}
function browserAsset(source:string,mime:string,site=false):Response {
  return new Response(source,{headers:{
    'content-type':mime+'; charset=utf-8','cache-control':'no-store',
    'x-content-type-options':'nosniff','referrer-policy':'no-referrer',
    'content-security-policy':site
      ? "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'"
      : "default-src 'none'; base-uri 'none'; frame-ancestors 'none'"
  }});
}
function informationPage(title:string,content:string):Response {
  return html('<!doctype html><html lang="pt-BR"><meta charset="utf-8">'+
    '<meta name="viewport" content="width=device-width,initial-scale=1">'+
    '<title>'+esc(title)+'</title><body style="font:16px system-ui;max-width:700px;margin:35px auto;padding:0 20px;line-height:1.55">'+
    '<h1>'+esc(title)+'</h1>'+content+'<p><a href="'+esc(publicBase)+'/privacy">Privacidade</a></p></body></html>');
}
const privacyText='<p>O Orçamento Express armazena orçamentos, clientes e documentos no aparelho. '+
  'Quando você utiliza o Pro Online, guarda uma cópia dos dados, histórico de autorizações, '+
  'e-mail, registro da licença e sessão em infraestrutura Supabase protegida.</p>'+
  '<p>A Google Play processa compras e assinaturas. Os documentos compartilhados por você '+
  'podem ser enviados a serviços de terceiros, como WhatsApp ou e-mail.</p>'+
  '<p>Você pode exportar seus dados no próprio aplicativo e solicitar a exclusão da conta online '+
  'em Empresa → Minha conta → Excluir conta. A exclusão não cancela assinaturas na Google Play.</p>'+
  '<p>O backup online é versionado e não representa sincronização colaborativa em tempo real. '+
  'Para suporte ou questões de privacidade, utilize o contato informado na ficha oficial do aplicativo na Google Play.</p>';
Deno.serve(async (req:Request):Promise<Response>=>{
  try{
    const full=new URL(req.url).pathname;
    // Função única: /functions/v1/orcamento-api/v1/cloud/... etc.
    const root='/orcamento-api';
    const i=full.indexOf(root);
    const route=i<0?full:full.slice(i+root.length)||'/';
    if(route==='/health' && req.method==='GET')return json({ok:true,provider:'supabase'});
    if(route==='/app' && req.method==='GET')return browserAsset(webHtml,'text/html',true);
    if(route==='/app.js' && req.method==='GET')return browserAsset(webJs,'text/javascript');
    if(route==='/style.css' && req.method==='GET')return browserAsset(webCss,'text/css');
    if(route==='/privacy' && req.method==='GET')
      return informationPage('Política de privacidade — Orçamento Express',privacyText);
    if(route==='/account-deletion' && req.method==='GET')
      return informationPage('Exclusão de conta — Orçamento Express',
        '<p>Para excluir a conta online: abra o aplicativo e selecione Empresa, Minha conta, '+
        'Excluir minha conta e backups online. Os orçamentos salvos no celular não são excluídos.</p>'+
        '<p>Cancelamentos de assinatura são gerenciados diretamente pela Google Play.</p>');
    if(route.startsWith('/a/'))return await approval(req,route);
    if(route.startsWith('/v1/play/'))return await playRoute(req,route);
    if(route.startsWith('/v1/cloud/'))return await cloud(req,route);
    return json({error:'not_found'},404);
  }catch(e:any) {
    const status=e instanceof ApiError?e.status:500;
    if(status>=500)console.error('oe edge: '+String(e?.message||'unknown').slice(0,90));
    return json({error:status>=500?'service_unavailable':e.message},status);
  }
});

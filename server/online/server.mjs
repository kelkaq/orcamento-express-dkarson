import {createServer} from 'node:http';
import {createHash,randomInt} from 'node:crypto';
import {Pool} from 'pg';
import nodemailer from 'nodemailer';
import {GoogleAuth} from 'google-auth-library';
import {assertBackup,assertQuoteForApproval,createCrypto,esc,hashSecret,
  normalizeEmail,otpHash,safeEqualHex,subscriptionStatus,token} from './lib.mjs';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {dirname,join} from 'node:path';

const __dirname=dirname(fileURLToPath(import.meta.url));
const config={
  port:Number(process.env.PORT||8089),
  publicUrl:String(process.env.PUBLIC_BASE_URL||'').replace(/\/$/,''),
  packageName:process.env.PLAY_PACKAGE_NAME||'com.dkarson.orcamentoexpress',
  subscription:process.env.PLAY_SUBSCRIPTION_ID||'orcamento_express_pro_mensal',
  otpKey:process.env.OTP_HMAC_KEY||'',
  smtpHost:process.env.SMTP_HOST||'',
  smtpPort:Number(process.env.SMTP_PORT||587),
  mailFrom:process.env.MAIL_FROM||'',
  dbUrl:process.env.DATABASE_URL||''
};
if(!config.publicUrl.startsWith('https://') || config.otpKey.length<32 ||
   !config.dbUrl || !config.smtpHost || !config.mailFrom)
  throw new Error('Configuração segura incompleta. Revise server/online/.env.example');
const crypt=createCrypto(process.env.BACKUP_ENCRYPTION_KEY_BASE64);
const pool=new Pool({connectionString:config.dbUrl, max:8, ssl:
  process.env.DATABASE_SSL==='true'?{rejectUnauthorized:true}:undefined});
const mail=nodemailer.createTransport({
  host:config.smtpHost,port:config.smtpPort,
  secure:process.env.SMTP_SECURE==='true',
  auth:{user:process.env.SMTP_USER,pass:process.env.SMTP_PASSWORD}
});
const google=new GoogleAuth({scopes:['https://www.googleapis.com/auth/androidpublisher']});
const requestRates=new Map();
const rateTimer=setInterval(()=>{
  for(const [key,val] of requestRates) if(Date.now()-val.at>120000)requestRates.delete(key);
},120000);rateTimer.unref();

async function sqlSchema() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY, email TEXT NOT NULL UNIQUE,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      play_token_cipher TEXT, play_token_hash TEXT UNIQUE,
      online_expires_at TIMESTAMPTZ, play_checked_at TIMESTAMPTZ
    );
    CREATE TABLE IF NOT EXISTS otp_codes (
      email TEXT PRIMARY KEY, code_hash TEXT NOT NULL,
      attempts INTEGER NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      expires_at TIMESTAMPTZ NOT NULL
    );
    CREATE TABLE IF NOT EXISTS sessions (
      token_hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      expires_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
    CREATE TABLE IF NOT EXISTS cloud_backups (
      user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
      revision BIGINT NOT NULL, encrypted_json TEXT NOT NULL,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE TABLE IF NOT EXISTS approval_links (
      id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      token_hash TEXT NOT NULL UNIQUE,
      quote_number TEXT NOT NULL, encrypted_snapshot TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'PENDING',
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      expires_at TIMESTAMPTZ NOT NULL,
      responded_at TIMESTAMPTZ
    );
    CREATE INDEX IF NOT EXISTS idx_approval_owner ON approval_links(user_id,responded_at);
  `);
}
function reply(res,status,data,headers={}) {
  res.writeHead(status,{
    'Cache-Control':'no-store',
    'X-Content-Type-Options':'nosniff',
    'Referrer-Policy':'no-referrer',
    'Content-Type':'application/json; charset=utf-8',
    ...headers
  });
  res.end(JSON.stringify(data));
}
function page(res,html,status=200) {
  res.writeHead(status,{
    'Content-Type':'text/html; charset=utf-8',
    'Cache-Control':'no-store',
    'X-Content-Type-Options':'nosniff',
    'Referrer-Policy':'no-referrer',
    'X-Robots-Tag':'noindex, nofollow',
    'Content-Security-Policy':"default-src 'none'; style-src 'self'; script-src 'self'; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'"
  });
  res.end(html);
}
async function readBody(req,limit=6_500_000) {
  let raw='',size=0;
  for await(const chunk of req) {
    size+=chunk.length;
    if(size>limit) throw Object.assign(new Error('Arquivo muito grande'),{status:413});
    raw+=chunk.toString('utf8');
  }
  try{return JSON.parse(raw);}catch{throw Object.assign(new Error('JSON inválido'),{status:400});}
}
function limit(key,max=12,windowMs=60_000) {
  const now=Date.now();let row=requestRates.get(key);
  if(!row||now-row.at>windowMs)row={count:0,at:now};
  row.count++;requestRates.set(key,row);
  if(row.count>max)throw Object.assign(new Error('Muitas tentativas'),{status:429});
}
async function getSession(req) {
  const auth=String(req.headers.authorization||'');
  if(!auth.startsWith('Bearer '))throw Object.assign(new Error('Faça login'),{status:401});
  const provided=auth.substring(7).trim();
  if(provided.length<32||provided.length>200)
    throw Object.assign(new Error('Sessão inválida'),{status:401});
  const result=await pool.query(`SELECT u.* FROM sessions s
    JOIN users u ON u.id=s.user_id
    WHERE s.token_hash=$1 AND s.expires_at>NOW() LIMIT 1`,[hashSecret(provided)]);
  if(result.rowCount===0)throw Object.assign(new Error('Sessão expirada'),{status:401});
  return result.rows[0];
}
async function googleSubscription(purchaseToken) {
  const bearer=await google.getAccessToken();
  if(!bearer)throw new Error('Google Play API não autorizada');
  const url='https://androidpublisher.googleapis.com/androidpublisher/v3/applications/' +
    encodeURIComponent(config.packageName) + '/purchases/subscriptionsv2/tokens/' +
    encodeURIComponent(purchaseToken);
  const response=await fetch(url,{headers:{Authorization:'Bearer '+bearer}});
  if(!response.ok)throw Object.assign(new Error('Google Play não confirmou assinatura'),
    {status:response.status===404?402:503});
  return subscriptionStatus(await response.json(),config.subscription);
}
async function requireOnline(user) {
  if(!user.play_token_cipher || !user.online_expires_at ||
     new Date(user.online_expires_at).getTime()<=Date.now())
    throw Object.assign(new Error('Plano Online inativo'),{status:402});
  const checked=new Date(user.play_checked_at||0).getTime();
  if(Date.now()-checked<5*60_000)return;
  // Cache do servidor limitado a cinco minutos, revoga acesso sob cancelamento/reembolso.
  const purchaseToken=crypt.open(user.play_token_cipher);
  const status=await googleSubscription(purchaseToken);
  if(!status.active) {
    await pool.query(`UPDATE users SET online_expires_at=NULL, play_checked_at=NOW()
      WHERE id=$1`,[user.id]);
    throw Object.assign(new Error('Plano Online suspenso/expirado'),{status:402});
  }
  await pool.query(`UPDATE users SET online_expires_at=to_timestamp($1/1000.0),
    play_checked_at=NOW() WHERE id=$2`,[status.expiresAtMillis,user.id]);
}
async function handleCloud(req,res,path) {
  if(req.method==='POST' && path==='/v1/cloud/request-code') {
    const body=await readBody(req,1024);
    const email=normalizeEmail(body.email);
    limit('otp_email:'+email,3,5*60_000);
    const code=String(randomInt(0,1_000_000)).padStart(6,'0');
    const hashed=otpHash(email,code,config.otpKey);
    await pool.query(`INSERT INTO otp_codes(email,code_hash,expires_at)
      VALUES($1,$2,NOW()+INTERVAL '10 minutes')
      ON CONFLICT(email) DO UPDATE SET
        code_hash=EXCLUDED.code_hash,attempts=0,created_at=NOW(),
        expires_at=EXCLUDED.expires_at`,[email,hashed]);
    await mail.sendMail({
      from:config.mailFrom,to:email,
      subject:'Código de acesso — DKARSON',
      text:'Seu código de acesso é '+code+'. Válido por 10 minutos. Não compartilhe.'
    });
    return reply(res,200,{sent:true});
  }
  if(req.method==='POST' && path==='/v1/cloud/verify-code') {
    const body=await readBody(req,1024),email=normalizeEmail(body.email);
    const code=String(body.code||'');
    if(!/^\d{6}$/.test(code))return reply(res,400,{error:'invalid_code'});
    limit('verify:'+email,10);
    const db=await pool.connect();
    try {
      await db.query('BEGIN');
      const row=await db.query('SELECT * FROM otp_codes WHERE email=$1 FOR UPDATE',[email]);
      const otp=row.rows[0];
      if(!otp||otp.attempts>=5||new Date(otp.expires_at).getTime()<Date.now()||
         !safeEqualHex(otp.code_hash,otpHash(email,code,config.otpKey))) {
        if(otp)await db.query('UPDATE otp_codes SET attempts=attempts+1 WHERE email=$1',[email]);
        await db.query('COMMIT');
        return reply(res,401,{error:'invalid_or_expired_code'});
      }
      await db.query('DELETE FROM otp_codes WHERE email=$1',[email]);
      const userId=token();
      await db.query(`INSERT INTO users(id,email) VALUES($1,$2)
        ON CONFLICT(email) DO NOTHING`,[userId,email]);
      const user=await db.query('SELECT id FROM users WHERE email=$1',[email]);
      const loginToken=token();
      await db.query(`INSERT INTO sessions(token_hash,user_id,expires_at)
        VALUES($1,$2,NOW()+INTERVAL '30 days')`,
        [hashSecret(loginToken),user.rows[0].id]);
      await db.query('COMMIT');
      return reply(res,200,{token:loginToken,expiresInDays:30});
    } catch(e){await db.query('ROLLBACK');throw e;} finally{db.release();}
  }
  const user=await getSession(req);
  if(req.method==='POST' && path==='/v1/cloud/link-subscription') {
    const body=await readBody(req,5000);
    const purchaseToken=String(body.purchaseToken||'');
    if(purchaseToken.length<16||purchaseToken.length>4096)
      return reply(res,400,{error:'invalid_token'});
    const status=await googleSubscription(purchaseToken);
    if(!status.active) return reply(res,402,{active:false});
    try {
      await pool.query(`UPDATE users SET play_token_cipher=$1,play_token_hash=$2,
        online_expires_at=to_timestamp($3/1000.0),play_checked_at=NOW()
        WHERE id=$4`,
        [crypt.seal(purchaseToken),hashSecret(purchaseToken),status.expiresAtMillis,user.id]);
    } catch(e) {
      if(e.code==='23505')return reply(res,409,{error:'purchase_already_linked'});
      throw e;
    }
    return reply(res,200,{active:true,expiresAtMillis:status.expiresAtMillis});
  }
  await requireOnline(user);

  if(req.method==='GET' && path==='/v1/cloud/backup') {
    const found=await pool.query('SELECT * FROM cloud_backups WHERE user_id=$1',[user.id]);
    if(found.rowCount===0)return reply(res,404,{error:'no_cloud_backup'});
    const row=found.rows[0];
    return reply(res,200,{revision:Number(row.revision),backup:crypt.open(row.encrypted_json)});
  }
  if(req.method==='POST' && path==='/v1/cloud/backup') {
    const body=await readBody(req,6_500_000);
    assertBackup(body.backup);
    const expected=Number(body.expectedRevision);
    if(!Number.isSafeInteger(expected)||expected<0)
      return reply(res,400,{error:'expected_revision_invalid'});
    const db=await pool.connect();
    try {
      await db.query('BEGIN');
      await db.query(`INSERT INTO cloud_backups(user_id,revision,encrypted_json)
        VALUES($1,0,$2) ON CONFLICT(user_id) DO NOTHING`,
        [user.id,crypt.seal('{}')]);
      const previous=await db.query(`SELECT revision FROM cloud_backups
        WHERE user_id=$1 FOR UPDATE`,[user.id]);
      const revision=Number(previous.rows[0].revision);
      if(revision!==expected) {
        await db.query('ROLLBACK');
        return reply(res,409,{error:'revision_conflict',currentRevision:revision});
      }
      const next=revision+1;
      await db.query(`UPDATE cloud_backups
        SET revision=$1,encrypted_json=$2,updated_at=NOW() WHERE user_id=$3`,
        [next,crypt.seal(body.backup),user.id]);
      await db.query('COMMIT');
      return reply(res,200,{revision:next});
    } catch(e){await db.query('ROLLBACK');throw e;} finally{db.release();}
  }
  if(req.method==='POST' && path==='/v1/cloud/approval-links') {
    const data=assertQuoteForApproval(await readBody(req,80_000));
    limit('approval:'+user.id,40,60_000);
    const rawToken=token(),id=token();
    await pool.query(`INSERT INTO approval_links
      (id,user_id,token_hash,quote_number,encrypted_snapshot,expires_at)
      VALUES($1,$2,$3,$4,$5,NOW()+INTERVAL '14 days')`,
      [id,user.id,hashSecret(rawToken),data.quoteNumber,
        crypt.seal(JSON.stringify(data))]);
    return reply(res,200,{url:config.publicUrl+'/a/'+encodeURIComponent(rawToken),
      expiresInDays:14});
  }
  if(req.method==='GET' && path==='/v1/cloud/decisions') {
    const rows=await pool.query(`SELECT DISTINCT ON (quote_number)
      quote_number,status,responded_at FROM approval_links
      WHERE user_id=$1 AND status IN ('APPROVED','REJECTED')
      ORDER BY quote_number,responded_at DESC`,[user.id]);
    return reply(res,200,{decisions:rows.rows.map(x=>({
      quoteNumber:x.quote_number,status:x.status,
      respondedAt:x.responded_at?.toISOString()
    }))});
  }
  if(req.method==='GET' && path==='/v1/cloud/quotes') {
    const result=await pool.query('SELECT encrypted_json,revision FROM cloud_backups WHERE user_id=$1',[user.id]);
    if(!result.rowCount)return reply(res,200,{quotes:[],revision:0});
    const snapshot=assertBackup(crypt.open(result.rows[0].encrypted_json));
    return reply(res,200,{
      quotes:snapshot.quotes.map(q=>({
        quoteNumber:q.quoteNumber,clientName:q.clientName,
        status:q.status,total:q.total,issueDate:q.issueDate
      })),
      revision:Number(result.rows[0].revision)
    });
  }
  return reply(res,404,{error:'not_found'});
}
function approvalHtml(quote, token, status='PENDING') {
  const rows=quote.items.map(item=>`<li>${esc(item.description)} —
    ${esc(item.quantity)} ${esc(item.unit)} × R$ ${Number(item.unitPrice).toFixed(2)}</li>`).join('');
  const buttons=status==='PENDING'
    ? `<form method="POST" action="/a/${encodeURIComponent(token)}/respond">
        <p>Ao confirmar, sua resposta será registrada com data e hora.</p>
        <button type="submit" name="decision" value="APPROVED">Aprovar orçamento</button>
        <button type="submit" name="decision" value="REJECTED">Recusar orçamento</button>
       </form>`
    : `<p><strong>Resposta registrada: ${status==='APPROVED'?'APROVADO':'RECUSADO'}.</strong></p>`;
  return `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1"><title>Aprovar orçamento — DKARSON</title>
    <link rel="stylesheet" href="/style.css"></head><body><main>
    <h1>Orçamento ${esc(quote.quoteNumber)}</h1>
    <p>Cliente: ${esc(quote.clientName)}</p>
    <h2>Itens</h2><ul>${rows}</ul>
    <h2>Valor total: R$ ${Number(quote.total).toFixed(2)}</h2>
    ${buttons}
    <small>Este registro é um aceite comercial eletrônico, não uma assinatura digital certificada
    nem nota fiscal. Confira o orçamento completo recebido do profissional antes de confirmar.</small>
    </main></body></html>`;
}
async function handleApproval(req,res,path) {
  const match=/^\/a\/([A-Za-z0-9_-]{30,80})(?:\/respond)?$/.exec(path);
  if(!match)return reply(res,404,{error:'not_found'});
  const rawToken=match[1];
  limit('approval_view:'+hashSecret(rawToken),60,60_000);
  const record=await pool.query(`SELECT * FROM approval_links
    WHERE token_hash=$1 AND expires_at>NOW() LIMIT 1`,[hashSecret(rawToken)]);
  if(!record.rowCount)return page(res,'<h1>Link expirado ou inexistente.</h1>',404);
  const entry=record.rows[0];
  if(req.method==='GET' && !path.endsWith('/respond')) {
    return page(res,approvalHtml(JSON.parse(crypt.open(entry.encrypted_snapshot)),
      rawToken,entry.status));
  }
  if(req.method==='POST' && path.endsWith('/respond')) {
    let raw='',size=0;
    for await(const chunk of req) {
      size+=chunk.length;if(size>1024) return page(res,'<p>Solicitação inválida.</p>',413);
      raw+=chunk.toString('utf8');
    }
    const decision=new URLSearchParams(raw).get('decision');
    if(!['APPROVED','REJECTED'].includes(decision))
      return page(res,'<p>Resposta inválida.</p>',400);
    // Mudança atômica, respostas duplicadas não podem substituir decisão anterior.
    const changed=await pool.query(`UPDATE approval_links SET status=$1,responded_at=NOW()
      WHERE token_hash=$2 AND status='PENDING' AND expires_at>NOW()
      RETURNING encrypted_snapshot,status`,[decision,hashSecret(rawToken)]);
    const row=changed.rows[0]||entry;
    return page(res,approvalHtml(JSON.parse(crypt.open(row.encrypted_snapshot)),
      rawToken,row.status));
  }
  return reply(res,405,{error:'method_not_allowed'});
}
const handler=createServer(async(req,res)=>{
  const path=new URL(req.url||'/',config.publicUrl).pathname;
  try {
    if(req.method==='GET' && path==='/health')return reply(res,200,{ok:true});
    if(req.method==='GET' && path==='/app') {
      const source=await readFile(join(__dirname,'web','app.html'));
      return page(res,source.toString('utf8'));
    }
    if(req.method==='GET' && (path==='/app.js'||path==='/style.css')) {
      const name=path==='/app.js'?'app.js':'style.css';
      const data=await readFile(join(__dirname,'web',name));
      res.writeHead(200,{
        'Content-Type':path==='/app.js'?'text/javascript; charset=utf-8':'text/css; charset=utf-8',
        'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'
      });
      return res.end(data);
    }
    if(path.startsWith('/a/'))return await handleApproval(req,res,path);
    if(path.startsWith('/v1/cloud/')) {
      limit('ip:'+req.socket.remoteAddress,200);
      return await handleCloud(req,res,path);
    }
    reply(res,404,{error:'not_found'});
  } catch(e) {
    const status=Number(e.status)||((e.code==='23505')?409:500);
    // Não registrar tokens, e-mails, senhas ou conteúdo de backups.
    if(status>=500)console.error('Falha online:',e.message?.slice(0,120));
    reply(res,status,{error:status>=500?'service_unavailable':e.message});
  }
});
await sqlSchema();
handler.listen(config.port,'127.0.0.1',
  ()=>console.log('DKARSON Online ativo somente em localhost:'+config.port));

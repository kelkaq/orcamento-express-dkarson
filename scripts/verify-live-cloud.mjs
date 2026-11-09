/**
 * E2E real contra a API Supabase em HTTPS, sem credenciais externas.
 * Cria uma conta DESCARTÁVEL, confirma isolamento, rotaciona a chave e exclui a conta.
 * Não imprime chaves de recuperação, tokens, dados de clientes ou segredos.
 */
import assert from 'node:assert/strict';

const base=(process.env.CLOUD_API_URL||'').replace(/\/$/,'');
if(!/^https:\/\/[a-z0-9-]+\.supabase\.co\/functions\/v1\/orcamento-api$/.test(base))
  throw new Error('CLOUD_API_URL deve ser a API HTTPS deste projeto Supabase');
let key='';
let session='';
let lastId='';
async function api(path,method='GET',data,credential) {
  const controller=new AbortController();
  const timeout=setTimeout(()=>controller.abort(),15000);
  try {
    const response=await fetch(base+path,{
      method,signal:controller.signal,
      headers:{'content-type':'application/json',
        ...(credential?{'authorization':'Bearer '+credential}:{})},
      body:data===undefined?undefined:JSON.stringify(data)
    });
    const raw=await response.text();
    const json=raw?JSON.parse(raw):{};
    return {status:response.status,json};
  }finally{clearTimeout(timeout);}
}
function check(name,pass) {
  assert.equal(pass,true,'Falhou: '+name);
  process.stdout.write('OK: '+name+'\n');
}

try {
  let resp=await api('/health');
  check('API respondeu ao diagnóstico',resp.status===200&&resp.json.ok===true);

  resp=await api('/v1/cloud/create-private-account','POST',{});
  check('Criou conta sem e-mail por HTTPS',
    resp.status===200&&/^[A-Za-z0-9_-]{43}$/.test(resp.json.recoveryKey||'')&&
    typeof resp.json.accountId==='string'&&typeof resp.json.token==='string');
  key=resp.json.recoveryKey;
  lastId=resp.json.accountId;
  session=resp.json.token;

  resp=await api('/v1/cloud/quotes','GET',undefined,session);
  check('Conta sem assinatura não tem acesso a orçamentos na nuvem',resp.status===402);

  resp=await api('/v1/cloud/recover-private-account','POST',{recoveryKey:key});
  check('Recuperou a mesma conta em outra sessão',
    resp.status===200&&resp.json.accountId===lastId);
  session=resp.json.token;

  resp=await api('/v1/cloud/rotate-recovery-key','POST',{},session);
  check('Alterou chave de recuperação com conta autenticada',
    resp.status===200&&/^[A-Za-z0-9_-]{43}$/.test(resp.json.recoveryKey||'')&&
    resp.json.recoveryKey!==key&&typeof resp.json.token==='string');
  const oldKey=key;key=resp.json.recoveryKey;session=resp.json.token;

  resp=await api('/v1/cloud/recover-private-account','POST',{recoveryKey:oldKey});
  check('Chave anterior deixou de funcionar',resp.status===401);

  resp=await api('/v1/cloud/recover-private-account','POST',{recoveryKey:key});
  check('Nova chave recupera a conta original',resp.status===200&&resp.json.accountId===lastId);
  session=resp.json.token;

  resp=await api('/v1/cloud/account','DELETE',{confirm:'EXCLUIR MINHA CONTA'},session);
  check('Exclusão da conta e dados online confirmada',resp.status===200&&resp.json.deleted===true);
  session='';

  resp=await api('/v1/cloud/recover-private-account','POST',{recoveryKey:key});
  check('Conta excluída não permite recuperar acesso',resp.status===401);
  process.stdout.write('E2E REAL CONCLUÍDO: todos os fluxos públicos validados.\n');
}finally{
  if(session){
    try {
      const result=await api('/v1/cloud/account','DELETE',
        {confirm:'EXCLUIR MINHA CONTA'},session);
      process.stdout.write('Limpeza da conta descartável: HTTP '+result.status+'\n');
    }catch{
      process.stdout.write('Atenção: limpeza automática falhou; verificar conta descartável.\n');
    }
  }
}

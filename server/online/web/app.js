const login=document.querySelector('#login');
const dash=document.querySelector('#dashboard');
const notice=document.querySelector('#notice');
const codeBox=document.querySelector('#codeBox');
const quoteBox=document.querySelector('#quotes');
const tokenKey='dkarson_session_token';
let token=sessionStorage.getItem(tokenKey);
function say(message){notice.textContent=String(message);}
async function api(path,options={}) {
  const headers={'Accept':'application/json','Content-Type':'application/json',...options.headers};
  if(token)headers.Authorization='Bearer '+token;
  const response=await fetch(path,{method:options.method||'GET',headers,body:options.body?JSON.stringify(options.body):undefined,
    credentials:'omit',cache:'no-store'});
  if(!response.ok) {
    if(response.status===401) {
      token=null;sessionStorage.removeItem(tokenKey);login.hidden=false;dash.hidden=true;
    }
    throw new Error({401:'Sessão expirada.',402:'Plano Online não confirmado no servidor.',
      429:'Muitas tentativas. Aguarde.',503:'Serviço indisponível.'}[response.status]||
      'Não foi possível consultar o servidor (HTTP '+response.status+').');
  }
  return response.json();
}
async function load() {
  if(!token){login.hidden=false;dash.hidden=true;return;}
  login.hidden=true;dash.hidden=false;
  say('Consultando orçamentos...');
  try {
    const data=await api('/v1/cloud/quotes');
    document.querySelector('#revision').textContent='Versão de backup: '+data.revision;
    quoteBox.replaceChildren();
    if(!data.quotes.length)quoteBox.textContent='Ainda não há orçamentos enviados do celular.';
    const money=new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'});
    for(const quote of data.quotes) {
      const row=document.createElement('article');
      const title=document.createElement('strong');
      title.textContent=quote.quoteNumber+' — '+(quote.clientName||'Cliente');
      const detail=document.createElement('p');
      detail.textContent='Situação: '+quote.status+' · Valor: '+money.format(Number(quote.total)||0);
      row.append(title,detail);quoteBox.append(row);
    }
    say('Histórico consultado.');
  } catch(error){say(error.message);}
}
document.querySelector('#request').addEventListener('click',async()=>{
  say('Solicitando código...');
  try {
    await api('/v1/cloud/request-code',{method:'POST',body:{
      email:document.querySelector('#email').value}});
    codeBox.hidden=false;
    say('Confira o código enviado para seu e-mail.');
  } catch(error){say(error.message);}
});
document.querySelector('#confirm').addEventListener('click',async()=>{
  try {
    const result=await api('/v1/cloud/verify-code',{method:'POST',body:{
      email:document.querySelector('#email').value,
      code:document.querySelector('#code').value
    }});
    token=result.token;sessionStorage.setItem(tokenKey,token);
    await load();
  }catch(error){say(error.message);}
});
document.querySelector('#refresh').addEventListener('click',load);
document.querySelector('#logout').addEventListener('click',()=>{
  token=null;sessionStorage.removeItem(tokenKey);
  login.hidden=false;dash.hidden=true;say('Você saiu da Central Online.');
});
load();

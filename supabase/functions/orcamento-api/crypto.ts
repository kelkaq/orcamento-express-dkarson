import {b64url,fromB64url,bytes} from './lib.mjs';
import {createClient} from 'npm:@supabase/supabase-js@2';

// A chave de criptografia é aleatória e mantida em tabela privada; não é exposta no APK.
// Ao restaurar backups completos do banco, manter a tabela oe_crypto_keys.
let cachedKey: CryptoKey | null=null;
async function aesKey(): Promise<CryptoKey> {
  if(cachedKey) return cachedKey;
  const host=Deno.env.get('SUPABASE_URL');
  const secret=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if(!host||!secret)throw new Error('Configuração de proteção de dados indisponível');
  const svc=createClient(host,secret,{auth:{persistSession:false,autoRefreshToken:false}});
  const result=await svc.from('oe_crypto_keys').select('key_base64')
    .eq('key_id','primary-v1').single();
  if(result.error||!result.data?.key_base64)
    throw new Error('Chave de proteção de dados indisponível');
  const raw=Uint8Array.from(atob(result.data.key_base64),ch=>ch.charCodeAt(0));
  if(raw.length!==32)throw new Error('Chave AES inválida');
  cachedKey=await crypto.subtle.importKey('raw',raw,'AES-GCM',false,['encrypt','decrypt']);
  return cachedKey;
}
export async function seal(plain: string): Promise<string> {
  const iv=crypto.getRandomValues(new Uint8Array(12));
  const cipher=new Uint8Array(await crypto.subtle.encrypt(
    {name:'AES-GCM',iv},await aesKey(),bytes(plain)));
  const all=new Uint8Array(iv.length+cipher.length);
  all.set(iv);all.set(cipher,12);
  return b64url(all);
}
export async function open(encoded: string): Promise<string> {
  const payload=fromB64url(encoded);
  if(payload.length<29)throw new Error('Registro criptografado inválido');
  const plain=await crypto.subtle.decrypt({name:'AES-GCM',iv:payload.slice(0,12)},
    await aesKey(),payload.slice(12));
  return new TextDecoder().decode(plain);
}

import {b64url,fromB64url,bytes} from './lib.mjs';

/** AES-256-GCM secret never leaves Edge Functions. */
async function aesKey() {
  const value=Deno.env.get('OE_ENCRYPTION_KEY_BASE64');
  if(!value)throw new Error('Chave de criptografia não configurada');
  const key=Uint8Array.from(atob(value), x=>x.charCodeAt(0));
  if(key.length!==32)throw new Error('Chave AES inválida');
  return crypto.subtle.importKey('raw',key,{name:'AES-GCM'},false,['encrypt','decrypt']);
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

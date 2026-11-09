-- Acessos sem serviço de e-mail: chave de recuperação de 256 bits, só o hash é persistido.
CREATE TABLE IF NOT EXISTS public.oe_recovery_keys (
  user_id UUID PRIMARY KEY REFERENCES public.oe_users(id) ON DELETE CASCADE,
  key_hash TEXT NOT NULL UNIQUE CHECK (length(key_hash) = 43),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  rotated_at TIMESTAMPTZ
);
ALTER TABLE public.oe_recovery_keys ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.oe_recovery_keys FROM PUBLIC,anon,authenticated;
GRANT ALL ON public.oe_recovery_keys TO service_role;

-- Chave AES de longa duração, restrita à API privilegiada. Não é segredo entregue no app.
CREATE TABLE IF NOT EXISTS public.oe_crypto_keys (
  key_id TEXT PRIMARY KEY,
  key_base64 TEXT NOT NULL CHECK (length(key_base64) = 44),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.oe_crypto_keys ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.oe_crypto_keys FROM PUBLIC,anon,authenticated;
GRANT ALL ON public.oe_crypto_keys TO service_role;
INSERT INTO public.oe_crypto_keys(key_id,key_base64)
VALUES ('primary-v1',encode(gen_random_bytes(32),'base64'))
ON CONFLICT(key_id) DO NOTHING;

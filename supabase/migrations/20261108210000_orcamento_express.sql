-- Tabelas exclusivas do Orçamento Express. Aplicar em projeto Supabase isolado.
-- Todas as operações passam por uma Edge Function com sessão verificável.
-- Nenhum acesso direto anônimo/autenticado a estas tabelas é permitido.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE IF NOT EXISTS public.oe_users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT NOT NULL UNIQUE,
  google_sub TEXT UNIQUE,
  auth_uid UUID UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS public.oe_sessions (
  token_hash TEXT PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES public.oe_users(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS oe_sessions_user_idx ON public.oe_sessions(user_id);
CREATE TABLE IF NOT EXISTS public.oe_subscriptions (
  user_id UUID PRIMARY KEY REFERENCES public.oe_users(id) ON DELETE CASCADE,
  purchase_token_hash TEXT NOT NULL UNIQUE,
  purchase_token_encrypted TEXT NOT NULL,
  expires_at TIMESTAMPTZ,
  checked_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS public.oe_backups (
  user_id UUID PRIMARY KEY REFERENCES public.oe_users(id) ON DELETE CASCADE,
  revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
  backup_encrypted TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS public.oe_approval_links (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES public.oe_users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE,
  quote_number TEXT NOT NULL,
  encrypted_snapshot TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN('PENDING','APPROVED','REJECTED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  responded_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS oe_approval_user_idx ON public.oe_approval_links(user_id,responded_at);
CREATE TABLE IF NOT EXISTS public.oe_limits (
  bucket TEXT PRIMARY KEY,
  requests INTEGER NOT NULL DEFAULT 0 CHECK(requests>=0),
  resets_at TIMESTAMPTZ NOT NULL
);
-- O limite precisa ser atômico mesmo sob concorrência entre regiões de Edge.
CREATE OR REPLACE FUNCTION public.oe_check_rate_limit(
  p_bucket TEXT,p_max INTEGER,p_seconds INTEGER
) RETURNS BOOLEAN LANGUAGE plpgsql SECURITY DEFINER
SET search_path=public AS $$
DECLARE current_count INTEGER;
BEGIN
  IF length(p_bucket)>200 OR p_max<1 OR p_max>1000 OR p_seconds<1 OR p_seconds>86400 THEN
    RETURN FALSE;
  END IF;
  INSERT INTO public.oe_limits(bucket,requests,resets_at)
    VALUES(p_bucket,1,now()+make_interval(secs=>p_seconds))
    ON CONFLICT(bucket) DO UPDATE SET
      requests=CASE WHEN public.oe_limits.resets_at<=now() THEN 1
         ELSE public.oe_limits.requests+1 END,
      resets_at=CASE WHEN public.oe_limits.resets_at<=now()
         THEN now()+make_interval(secs=>p_seconds)
         ELSE public.oe_limits.resets_at END
    RETURNING requests INTO current_count;
  RETURN current_count<=p_max;
END $$;
REVOKE ALL ON FUNCTION public.oe_check_rate_limit(TEXT,INTEGER,INTEGER) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.oe_check_rate_limit(TEXT,INTEGER,INTEGER) TO service_role;

DO $$
DECLARE obj TEXT;
BEGIN
  FOREACH obj IN ARRAY ARRAY['oe_users','oe_sessions','oe_subscriptions','oe_backups',
    'oe_approval_links','oe_limits'] LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',obj);
    EXECUTE format('REVOKE ALL ON public.%I FROM anon,authenticated',obj);
    EXECUTE format('GRANT ALL ON public.%I TO service_role',obj);
  END LOOP;
END $$;

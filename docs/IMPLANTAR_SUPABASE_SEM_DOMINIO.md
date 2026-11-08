# Implantação sem domínio e sem VPS — Orçamento Express

## Infraestrutura escolhida
- Android nativo + banco local Room: continua funcionando sem internet.
- Supabase PostgreSQL com tabelas `oe_*`: contas, sessões, backups versionados, aprovação por link, assinatura Pro Online.
- Supabase Auth: código por e-mail e login Google validados pelo provedor.
- Supabase Edge Function `orcamento-api`: consulta Google Play, controla acesso, criptografa backups e entrega a central web.
- Endereço HTTPS incluído: `https://<ref>.supabase.co/functions/v1/orcamento-api`. Não precisa comprar domínio ou manter VPS para este app.

## Princípios de segurança
- Não informar chaves privadas, tokens, senhas ou arquivos .jks na conversa, em screenshots ou no repositório.
- O banco e as Edge Functions são implantados com credenciais armazenadas no Supabase e em GitHub Actions Secrets.
- O app Android só conhece endpoints HTTPS e o **Google Web Client ID público**, nunca a service-role key.
- As tabelas são invisíveis aos clientes (RLS habilitada sem políticas, acesso via Edge Function após autenticação).
- A função usa serviço de rate limit atômico no banco para OTP e tentativas de compra.
- A criptografia AES-256-GCM usa chave privada separada; não trocar essa chave sem plano de migração de dados.

## Configuração externa única (exige identidade do titular)
1. No Supabase, criar um projeto isolado em sua conta, em região adequada, e obter o **Project Ref**.
2. Em Supabase Auth habilitar login Google, configurar OAuth Web client ID/secret no Google Cloud, e modelo de e-mail com token de seis dígitos. Configurar SMTP de produção: o e-mail padrão do plano gratuito não é suficiente para muitos clientes.
3. Na Google Play Console, criar aplicativo `com.dkarson.orcamentoexpress`, perfil de pagamentos, compra única `orcamento_express_pro_offline` e assinatura `orcamento_express_pro_mensal` com plano base `mensal`. Cadastrar valores.
4. Configurar no Google Cloud uma conta de serviço com permissão Android Publisher API na Play Console e um tópico Pub/Sub de notificações da Google Play.
5. Criar uma conta de serviço para Push Auth do Pub/Sub e habilitar notificações de assinaturas **e compras únicas**; o endpoint público será `<API>/v1/play/rtdn`.
6. Adicionar no GitHub Settings → Secrets & variables → Actions: secrets `SUPABASE_ACCESS_TOKEN`, `SUPABASE_DB_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON` (JSON bruto), `OE_ENCRYPTION_KEY_BASE64` (32 bytes aleatórios convertidos a Base64), `ANDROID_UPLOAD_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD` para Fastlane (a mesma chave JSON atende ambas as implantações).
7. Variáveis GitHub: `SUPABASE_PROJECT_REF`, `PUBSUB_PUSH_SERVICE_ACCOUNT_EMAIL`, `GOOGLE_WEB_CLIENT_ID`, `SUPPORT_EMAIL` (canal realmente ativo) e URLs opcionais dos serviços.
8. Após os testes, mesclar branch aprovada em `main`; disparar no GitHub Actions `Ativar Supabase — Orçamento Express`, que executa migrações, configura segredos, implanta a função e verifica HTTPS.
9. Executar teste interno da Google Play, incluindo pagamento com licença de teste, compra única, assinatura, cancelamento, reembolso, nova instalação e restauração; depois usar o workflow protegido de publicação para teste interno.
10. Completar política de privacidade, declarações de dados, preços, contato, screenshots e exigências de avaliação da Play Console. A política servida em `<API>/privacy` deve ser revisada com os dados reais de suporte, retenção e processamento antes de divulgação.

## URLs sem domínio próprio
```
CLOUD_API_URL=https://<ref>.supabase.co/functions/v1/orcamento-api
BILLING_VERIFY_URL=https://<ref>.supabase.co/functions/v1/orcamento-api/v1/play/verify
PUBLIC_EDGE_BASE_URL=https://<ref>.supabase.co/functions/v1/orcamento-api
PRIVACY_URL=https://<ref>.supabase.co/functions/v1/orcamento-api/privacy
ACCOUNT_DELETION_URL=https://<ref>.supabase.co/functions/v1/orcamento-api/account-deletion
WEB_HISTORY_URL=https://<ref>.supabase.co/functions/v1/orcamento-api/app
```

## APIs do cliente existentes
- `POST /v1/cloud/request-code` e `verify-code`: identidade por e-mail.
- `POST /v1/cloud/google-sign-in`, `link-google`: Google.
- `DELETE /v1/cloud/session`, `account`: saída e exclusão.
- `POST /v1/cloud/link-subscription`: vinculação de assinatura validada.
- `GET/POST /v1/cloud/backup`: cópia versionada sem sobrescrita silenciosa.
- `POST /v1/cloud/approval-links`, `GET /v1/cloud/decisions`: aprovação.
- `GET /v1/cloud/quotes`: consulta web com sessão e assinatura válidas.
- `POST /v1/play/verify`: verificação por token na API oficial do Google.
- `POST /v1/play/rtdn`: evento assinado Pub/Sub; estado reconfirmado na Google Play.

## O que foi testado
CI valida sintaxe e tipos Deno, helpers puros, backend Node legado e testes unitários Android. **A execução de contas, pagamentos e publicação em serviços reais não pode ser verificada sem projetos e credenciais configurados.** Não declarar a loja ativa ou o app comercialmente pronto somente com CI verde.

## Observações
- Backups na nuvem são *snapshots versionados*, não edição colaborativa em tempo real entre aparelhos. Restauração exige confirmação e preserva cópia preventiva.
- A compra única Pro Offline continua acessível após validação, até revisão de direito quando houver Play conectada. Reembolso em aparelho permanentemente offline não pode ser revogado instantaneamente.
- Pro Online exige assinatura ativa; Google Play controla cobranças, renovação e cancelamento.
- Exclusão de conta online não cancela assinatura na Google Play, que deve ser encerrada na loja.
- Projeto Supabase Free pode ser pausado por inatividade; para clientes pagantes, comparar disponibilidade e limites comerciais e planejar backups.

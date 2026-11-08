# Operação comercial — Google Play, login e PostgreSQL Supabase

## 1. Não publicar ainda
O APK de teste (.teste.unificado) serve para homologação no telefone. O AAB assinado de produção deve sair de `main` depois de consolidar a branch, com a chave privada do proprietário configurada somente em GitHub Secrets. Nunca coloque senhas, conta de serviço ou chaves em código-fonte.

## 2. Google Play (produtos)
- Aplicativo: `com.dkarson.orcamentoexpress`
- Compra única não consumível: `orcamento_express_pro_offline`.
- Assinatura renovável: `orcamento_express_pro_mensal`, plano base `mensal`.
- Cadastrar valores somente após aprovação comercial. O aplicativo exibe preços oficiais da Play.
- Configurar conta de serviço mínima para a Android Publisher API; backend HTTPS `/v1/play/verify`.
- Os tokens só geram direitos após verificação pelo backend, com restauração de compras pela Play.

## 3. Login Google
- Criar OAuth **Web client ID** no Google Cloud para verificar identidade no servidor (não é senha).
- Configurar `GOOGLE_WEB_CLIENT_ID` no servidor e na variável pública do GitHub Actions.
- Configurar identidade do app Android e assinatura SHA-1/SHA-256 para versões release e testes conforme a console.
- O app solicita um Google ID token pela Credential Manager; o servidor verifica assinatura, audiência, email_verificado e sub.
- Se o mesmo e-mail já existe por OTP, fazer login por e-mail/código e usar “Vincular minha conta Google”. Não há fusão silenciosa de contas.
- Sem OAuth configurado, login por e-mail/código permanece alternativa quando o servidor estiver no ar.

## 4. Banco: VPS ou Supabase
O servidor aceita a URL PostgreSQL `DATABASE_URL`. Para o Supabase, criar projeto e pegar a conexão do **session pooler** (porta 5432) ou conexão direta compatível com a VPS, habilitar TLS `DATABASE_SSL=true`. A senha PostgreSQL fica somente na VPS e não entra nas variáveis do build Android. A inicialização do backend cria tabelas do namespace da aplicação. **Não compartilhar o mesmo banco com outras aplicações em produção sem planejamento de schema/isolamento**.

Supabase Free serve para testes, mas pode ser suspenso; avaliar planos, cotas e backups antes da abertura comercial. O aplicativo continua com Room para funcionar offline. A nuvem atual trabalha com snapshots versionados, não edição multiusuário simultânea.

## 5. Renovação e cancelamento automáticos
- Criar tópico Google Cloud Pub/Sub para notificações do Google Play e dar permissão Publisher ao serviço oficial `google-play-developer-notifications@system.gserviceaccount.com`.
- Criar assinatura push **autenticada** HTTPS para `/v1/play/rtdn` e conta de serviço emissora.
- Configurar no servidor `PUBSUB_PUSH_AUDIENCE` igual à URL/audiência configurada e `PUBSUB_PUSH_SERVICE_ACCOUNT_EMAIL` igual à identidade esperada.
- O servidor verifica o JWT do Pub/Sub e consulta a Android Publisher API a cada notificação; não acredita no status enviado.
- Se uma notificação chegar antes do vínculo da compra com a conta, o aplicativo volta a conferir ao abrir e o servidor reconfirma o status em operações online.
- Testar ativa, pendente, vencida, cancelada, reembolsada, grace period, conta trocada e restauração. Nenhuma liberação depende de comando manual.

## 6. Segurança e lançamento
- Fazer backup criptografado do banco, HTTPS, SMTP confiável e logs sem dados pessoais.
- Política pública de privacidade, termos, contato do suporte, formulário de exclusão de dados e declarações de segurança da Play Console.
- Testar com usuários internos Android reais e avaliar a conformidade com LGPD e regras locais.
- Separar homologação e produção; nunca usar assinaturas fictícias na versão comercial.

Documentação oficial:
- https://developer.android.com/google/play/billing/getting-ready
- https://docs.cloud.google.com/pubsub/docs/authenticate-push-subscriptions
- https://supabase.com/docs/guides/database/connecting-to-postgres
- https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation

# Orçamento Express — DKARSON

Aplicativo Android nativo em **Kotlin + Jetpack Compose + Room**, com orçamentos, clientes, catálogo, custos e preços, ordens de serviço, pagamentos, recibos, agenda, notificações e backup.

## Versão de desenvolvimento 1.3 (08/11/2026)

O desenvolvimento ativo está na branch `feat/contas-sobre-homologacao-20261108`. A branch `main` continua na versão anterior até a homologação.

### Android
- **Gratuito**: até 5 novos orçamentos/mês, históricos, PDFs, clientes, agenda, calculadora simples e backup local.
- **Pro Offline**: compra única verificada via Google Play; orçamentos ilimitados, cálculo por profissão, controle de custos/lucro, modelos, ordens de serviço e recibos.
- **Pro Online**: assinatura mensal validada via Google Play; além das ferramentas Pro, login e backups na nuvem, histórico web somente leitura e aprovação por link.
- **Teste de proprietário**: apenas o APK debug tem botão “Ativar Pro de teste” na tela de planos. Não acessa servidor online e não desbloqueia compras reais.
- **Conta**: e-mail/código e login Google Credential Manager, com ID token validado pelo servidor quando a configuração está presente; vinculação protegida de contas existentes.
- **Sobre o aplicativo**: versão/código real do build, ambiente, plano, armazenamento e orientações de suporte.
- **Backup**: arquivos de dados exportáveis e restauração com backup preventivo; nunca apagar a versão anterior antes de verificar a exportação.

### Servidor e persistência
- `server/online`: Node 22, PostgreSQL e SMTP, tokens de sessão, autenticação, backups cifrados AES-256-GCM, aprovações, exclusão da conta e notificações de assinatura via Pub/Sub autenticado.
- `server/play-billing`: validação e reconhecimento de compras pela Android Publisher API.
- **Supabase opcional**: hospede o PostgreSQL do servidor com conexão SESSION/TLS. Não exponha credenciais do banco no APK. Usar Supabase NÃO substitui a API e as regras de acesso do servidor.

### Como testar
1. Abra **Actions → Gerar APK de teste — DKARSON** na branch de desenvolvimento.
2. Baixe `ORCAMENTO_EXPRESS_DKARSON_TESTE_APK` em Artifacts, extraia e instale.
3. Confira **Empresa → Conta e informações → Sobre o aplicativo**. Em **Conhecer Pro**, ative as ferramentas de teste e avalie orçamentos, custos, OS e recibos.
4. Rode testes pelo GitHub Actions; não confunda build de sucesso com testes manuais em telefone.
5. Conta Google e serviços Online só funcionam quando `CLOUD_API_URL`, `GOOGLE_WEB_CLIENT_ID` e o servidor estiverem configurados. O APK de teste não faz cobranças reais.

### Publicação comercial
É necessário **Play Console verificada**, compras cadastradas, assinatura configurada, API Android Publisher, chave privada de upload, HTTPS, domínio, banco e SMTP, política de privacidade, suporte, exclusão de conta e testes internos pela loja. Essas ações exigem configuração das contas do proprietário e não são automatizáveis apenas pelo repositório.

Veja `docs/PLANOS_E_PUBLICACAO.md`, `PLAY_STORE_LANCAMENTO.md` e `docs/OPERACAO_GOOGLE_E_SUPABASE.md` para os detalhes.

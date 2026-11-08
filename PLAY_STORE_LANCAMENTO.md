# Orçamento Express — lançamento Freemium + Pro

## Produto implementado

**Gratuito**: até 5 NOVOS orçamentos por mês civil; cadastro de clientes, catálogo, calculadora, PDFs de orçamentos, histórico, edição, compartilhamento de documentos já existentes e backup. O limite aplica-se no banco aos novos registros, não apenas na interface. Edições e documentos antigos permanecem acessíveis.

**Pro**: assinatura de renovação mensal com orçamentos ilimitados; criação/edição de ordens de serviço, registro de recebimentos, novos recibos, modelos reutilizáveis e acesso aos detalhes financeiros. Documentos antigos não são apagados nem bloqueados se o Pro expirar.

**Preço comercial pretendido**: **R$ 39,90 por mês no Brasil**. O app exibe o preço REAL retornado pela Google Play. Não codifique valores fictícios no app: quem define moeda, impostos e preço é o Play Console.

**Limitação operacional**: não é possível ativar um produto real de assinatura, nem gerar AAB de produção assinado, apenas a partir do código de um repositório. O desenvolvedor precisa concluir a identidade e a conta de pagamentos da Play, criar o produto, conceder acesso à API e cadastrar os segredos da assinatura. Não publique cobrando usuários antes da validação ponta a ponta por testadores internos.

## 1. Criar o produto de assinatura

Na Google Play Console, no app cujo applicationId é `com.dkarson.orcamentoexpress`:

- Crie a assinatura com ID imutável **`orcamento_express_pro_mensal`**.
- Crie plano base de **renovação automática mensal** com ID **`mensal`**.
- Configure o preço no Brasil para **R$ 39,90/mês** (o preço em outros países é definido na Console).
- Ative o produto somente após completar requisitos, informações sobre a assinatura e conta de pagamentos.
- Não anuncie avaliação gratuita ou desconto enquanto não houver oferta configurada no produto.
- Ofereça cancelamento pela Google Play. O aplicativo inclui link de gestão e restauração de compras.

## 2. Servidor de verificação (obrigatório para este código)

O Android **não** libera Pro só por receber um token de compra. O serviço seguro `server/play-billing` verifica diretamente na Google Play Developer API (Subscriptions V2), confirma productId, status e expiração, e reconhece a nova assinatura.

- VPS com Node.js >=20 e HTTPS atrás de proxy (Nginx/Caddy). O processo Node escuta apenas em localhost.
- Instale as dependências: `cd server/play-billing && npm install --omit=dev`.
- Habilite a **Google Play Android Publisher API** no projeto Google Cloud da sua conta.
- Crie conta de serviço com o menor conjunto de permissões necessário na Play Console, incluindo leitura de pedidos/assinaturas e reconhecimento de compras. Não disponibilize o arquivo JSON publicamente.
- Na VPS defina `PLAY_PACKAGE_NAME=com.dkarson.orcamentoexpress`, `PLAY_PRODUCT_ID=orcamento_express_pro_mensal`, `GOOGLE_APPLICATION_CREDENTIALS=/pasta-privada/service-account.json`.
- Serviço: `node server.mjs`; proxy HTTPS: `https://SEU_DOMINIO/v1/play/verify` → `http://127.0.0.1:8088/v1/play/verify`.
- O endpoint não deve ficar atrás de login interativo; o token Google Play é validado na API oficial. Configure limites de requisição, firewall e monitoramento do servidor.
- Não coloque as credenciais, senhas, service-account JSON ou tokens de compras no APK ou no GitHub.
- Configure em **GitHub → Settings → Secrets and variables → Actions → Variables**: `BILLING_VERIFY_URL=https://SEU_DOMINIO/v1/play/verify`. A compilação de lançamento incorpora somente a URL HTTPS, nunca as chaves. Com URL ausente, o botão Assinar permanece desabilitado (não vende algo que não consegue liberar).

## 3. Assinatura e AAB de publicação

O repositório mantém testes automatizados e gera APK de debug. O pacote debug (`.teste.unificado`) **não compra assinatura de produção**. Para testar cobrança real use um AAB release instalado por faixa de teste da Play e conta de **testador de licença**. O usuário dono da conta deve manter a upload key privada.

Na área **Secrets** das ações do GitHub configure:

- `ANDROID_UPLOAD_KEYSTORE_BASE64`: base64 do seu arquivo keystore de upload Android (.jks).
- `STORE_PASSWORD`: senha do keystore.
- `KEY_PASSWORD`: senha da chave alias `upload`.

A workflow gera `app-release.aab` somente no branch `main`, com os três segredos e `BILLING_VERIFY_URL` HTTPS definidos. Nunca use a chave `dkarson-test-only.p12` do APK de teste para a Play Store.

Depois, na Play Console, publique primeiro em **Teste interno**, instale pela loja e valide: assinatura, restauração, cancelamento, expiração, pagamento pendente e o limite de 5 orçamentos. Depois realize os requisitos de acesso à produção exigidos para a sua conta e publique.

## 4. Comportamento e segurança

- Confirmação de compra: somente `PurchaseState.PURCHASED`, seguido de verificação no backend Google Play.
- Assinaturas pendentes não liberam Pro. Estado on hold ou expirado não libera Pro.
- Assinatura cancelada permanece ativa até o final do período já pago, conforme retorno da API Google.
- Em caso de indisponibilidade, o app permite uso offline Pro por no máximo **72h desde a última verificação bem-sucedida**, nunca após a expiração confirmada. O app Gratuito continua funcionando offline.
- Um dispositivo com root pode adulterar armazenamento local. Proteção antifraude avançada, sincronização entre dispositivos e limites globais exigiriam contas de usuário e armazenamento de direitos em servidor, não apenas cache local.
- O usuário mantém orçamentos/recibos/OS anteriores ao término do Pro. Suporte e backups são gratuitos.
- A assinatura oferece valor recorrente: criar propostas ilimitadas todo mês e acesso continuado às ferramentas. Revise a política de assinaturas antes de publicar: https://support.google.com/googleplay/android-developer/answer/9900533?hl=pt-BR .
- Para comercialização: a Play Console pode exigir política de privacidade pública, declaração de segurança de dados, ficha do app e termos de assinatura. Garanta URLs públicas reais antes da revisão.

## 5. Plano de validação comercial

Não existe garantia de chegar a R$ 10 mil/mês. Antes de investir em anúncios, acompanhe taxa de instalação→cadastro de primeiro orçamento→5º orçamento→assinatura Pro→retenção/cancelamento, receita líquida após taxas e impostos. O aplicativo não inclui telemetria invasiva: métricas comerciais da Play Console podem ser usadas inicialmente.

# Orçamento Express — DKARSON

Aplicativo nativo Android para orçamentos profissionais de prestadores de serviços, com funcionamento offline.

## Versão do código: 1.4 (08/11/2026)

- **Gratuito:** até 5 novos orçamentos/mês, clientes, catálogo, calculadora simples, histórico, PDFs, agenda e backup local.
- **Pro Offline (compra única):** orçamentos ilimitados, formação de preços por profissão, custo/lucro estimado, modelos, ordens de serviço, controle de recebimentos e recibos PDF.
- **Pro Online (assinatura):** recursos Pro + backup na nuvem, histórico privado de consulta web e aprovação de orçamentos por link.
- **Modo de homologação:** somente no APK debug, recursos Pro Offline desbloqueáveis para testes sem cobrança. A versão release não possui esse mecanismo.
- **Conta:** Google / e-mail com código quando servidor configurado. Nenhuma conta é obrigatória para uso local. Cada instalação contém um único workspace local.
- **Sobre:** número e código reais da versão, situação do plano, privacidade e canal de exclusão.
- **Backend recomendado:** Supabase PostgreSQL + Supabase Auth + Edge Functions, HTTPS incluído sem domínio próprio ou VPS, com token de compra validado via Android Publisher API.

### Como verificar o código e os testes
O workflow **Gerar APK de teste — DKARSON** compila e executa os testes unitários Android. O workflow **Testar serviços online DKARSON** verifica backend Node legado, Edge Function Deno, contratos de autenticação, migração PostgreSQL17 e políticas de acesso.

Um CI verde **não equivale a ter realizado compras reais nem a publicar o app na Google Play**.

### Publicação comercial
- Crie/verifique uma conta Google Play Console e prepare produtos comprados/assinatura.
- Conecte um projeto Supabase e configure Auth Google, envio de código por e-mail, chave Google Play segura, notificações Pub/Sub e suporte.
- Cadastre os segredos apenas no GitHub Actions e no Supabase, nunca no código ou APK.
- Utilize o workflow **Ativar Supabase — Orçamento Express (manual)** quando os acessos estiverem prontos; posteriormente o workflow **Publicar AAB na Google Play — teste interno**.
- Realize testes reais de pagamento, cancelamento, perda de conexão, restauração, troca de celular e revisão da política de privacidade.

Consulte [implantação Supabase sem domínio](docs/IMPLANTAR_SUPABASE_SEM_DOMINIO.md), [planos](docs/PLANOS_E_PUBLICACAO.md) e [publicação anterior](PLAY_STORE_LANCAMENTO.md).

A operação web na nuvem é por snapshots com revisões, não sincronização colaborativa instantânea. Dados locais nunca são excluídos por vencimento do plano.

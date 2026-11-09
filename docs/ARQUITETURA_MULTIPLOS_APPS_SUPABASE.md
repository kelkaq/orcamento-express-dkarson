# DKARSON — organização de vários aplicativos no Supabase

## Decisão estrutural
Todos os aplicativos pertencem à organização **kelkaq's Org**, mas **cada aplicativo terá um projeto Supabase separado** quando armazenar dados, clientes ou receber pagamentos independentes.

Vantagens: isolamento de dados, autenticação por produto, migrations independentes, segredos separados, controle de custos e falhas confinadas. Uma falha ou alteração no banco de um aplicativo não deverá modificar outro.

Não misturar aplicativos diferentes no mesmo banco apenas para economizar um projeto, especialmente se atendem clientes diferentes.

## Primeiro produto
| Chave | Valor |
|---|---|
| Nome | Orçamento Express DKARSON |
| Projeto Supabase | hajxpqnmeabyvfctlygp |
| Região | sa-east-1 (São Paulo) |
| API | https://hajxpqnmeabyvfctlygp.supabase.co/functions/v1/orcamento-api |
| Banco | PostgreSQL 17 |
| Tabelas | oe_users, oe_sessions, oe_subscriptions, oe_backups, oe_approval_links, oe_limits |
| Acesso de clientes às tabelas | RLS ativo e sem permissões diretas de leitura/edição; servidor privado intermedeia |
| Implantação inicial | banco + Edge Function orcamento-api |
| Custos iniciais | projeto no plano Free, custo base consultado US$ 0/mês; consumo acima de limites e evolução comercial devem ser reavaliados |

## Convenção para os próximos produtos
1. Organização única, projetos separados por **produto e ambiente** quando necessário.
2. Nome: `Nome do Aplicativo — DKARSON` (ou marca real do produto).
3. Região inicial: `sa-east-1` quando a maioria dos usuários estiver no Brasil; avaliar latência, legislação e custos antes de decidir.
4. Cada repositório GitHub terá seus próprios esquemas, migrations, funções, variáveis públicas e segredos.
5. Cada Android package name, Google OAuth, assinatura e item de cobrança deve ser **exclusivo** da aplicação.
6. Nenhuma `SUPABASE_SERVICE_ROLE_KEY`, senha de banco, chave AES, conta de serviço Google ou chave de assinatura será inserida em APK, repo ou documentação pública.
7. RLS em todas as tabelas expostas; autenticação e autorização verificadas também nos serviços com privilégios.
8. Implantar primeiro com custo-base gratuito quando disponível, monitorar cotas e planejar migração antes de comercialização em escala.
9. Fluxos de clientes pagos devem ter verificações de compra/reembolso, exclusão de conta, backups confiáveis, logs, monitoramento e recuperação.
10. Antes de cada nova implantação: confirmar organização e custo estimado, criar projeto isolado, implantar migração, executar advisors de segurança e testes HTTPS.

## Restrições comerciais do primeiro projeto
O endereço HTTPS e banco estão implantados, mas **não estão comprovados** o login Google real, SMTP de produção, pagamentos e assinaturas via Google Play, criptografia AES configurada, envio de AAB assinado e publicação pública.
O Supabase Free pode pausar por inatividade e tem limites; isso precisa ser reavaliado para usuários comerciais.

## Operação atual
- URL de diagnóstico: https://hajxpqnmeabyvfctlygp.supabase.co/functions/v1/orcamento-api/health
- Política: https://hajxpqnmeabyvfctlygp.supabase.co/functions/v1/orcamento-api/privacy
- Exclusão: https://hajxpqnmeabyvfctlygp.supabase.co/functions/v1/orcamento-api/account-deletion
- Central web: https://hajxpqnmeabyvfctlygp.supabase.co/functions/v1/orcamento-api/app

Atualizado em 08/11/2026.

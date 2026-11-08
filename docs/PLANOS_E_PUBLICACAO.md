# Orçamento Express — Planos, recursos e lançamento
Atualização de novembro de 2026. **Preços ainda não definidos pelo proprietário.**

## Planos implementados no aplicativo

| Recurso | Gratuito | Pro Offline (compra única) | Pro Online (mensalidade) |
| --- | --- | --- | --- |
| Até cinco novos orçamentos/mês | Sim | — | — |
| Orçamentos ilimitados | Não | Sim | Sim |
| Consulta/edição de documentos antigos, clientes, PDFs e backup | Sim | Sim | Sim |
| Calculadora simples | Sim | Sim | Sim |
| Agenda de serviços, retornos, notificações locais | Sim | Sim | Sim |
| Calculadora por profissão, margem de venda e custos/lucro | Não | Sim | Sim |
| Modelos reutilizáveis, OS, recebimentos e recibos novos | Não | Sim | Sim |
| Relatório financeiro e de lucro estimado | Não | Sim | Sim |
| Backup automático local e restauração | Sim | Sim | Sim |
| Cópia na nuvem, login por e-mail, histórico no navegador (somente consulta), aprovação por link e importação de respostas | Não | Não | Sim |

Os documentos e recibos gerados anteriormente não são apagados nem bloqueados por uma assinatura cancelada. Dados sensíveis de custos não vão para PDFs do cliente.

## O que está pronto em código e o que exige ativação externa

A aplicação Android, as telas de compra, os bloqueios no banco, o fluxo de autenticação por e-mail, cópia de segurança, avaliação de orçamentos e servidor estão preparados no GitHub. Isso **NÃO significa que a cobrança ou a nuvem já estejam publicadas**. Para funcionar com usuários reais, precisam de:

1. **Conta Play Console aprovada**, perfil de pagamentos e identidade verificada. Criar dois produtos:
   - Produto único gerenciado **`orcamento_express_pro_offline`**, não consumível, preço único definido pela conta;
   - Assinatura **`orcamento_express_pro_mensal`**, plano-base **`mensal`**, preço mensal aprovado.
2. **Google Play Android Publisher API**, conta de serviço com permissões restritas e credencial guardada na VPS. O servidor de verificação `server/play-billing` valida os dois tipos de produto e reconhece compras após verificá-las. Sem essa verificação, os botões de compra ficam desabilitados.
3. **Servidor online na VPS**, Node.js 22, PostgreSQL 17, SMTP confiável, HTTPS, backups da base e chave secreta AES-256-GCM. Código em `server/online`. Não expor Node diretamente: os serviços ficam em localhost e um proxy HTTPS faz as rotas.
4. **Endpoints públicos**:
   - `https://api.seu-dominio/v1/play/verify` -> `http://127.0.0.1:8088/v1/play/verify`
   - `https://api.seu-dominio/v1/cloud/*` -> `http://127.0.0.1:8089/v1/cloud/*`
   - `https://api.seu-dominio/a/*`, `/app`, `/app.js`, `/style.css` -> `http://127.0.0.1:8089`.
5. Definir em GitHub Actions > Variables:
   - `BILLING_VERIFY_URL=https://api.seu-dominio/v1/play/verify`
   - `CLOUD_API_URL=https://api.seu-dominio`
6. Definir em GitHub Actions > Secrets os dados do seu **keystore de upload privado**, como já documentado em `PLAY_STORE_LANCAMENTO.md`, e distribuir o AAB release pelo Teste Interno da Google Play, não o APK de teste.
7. Validar compras pelo Google Play em conta de testador (pendente, aprovada, cancelada, reembolsada, restaurada e expirada), e fluxo de login, backup, restauração, aprovação/recusa, autorização de acesso, conflito entre dispositivos, mudança de celular, notificações e acesso offline.
8. Publicar política de privacidade, termos de assinatura, suporte de contato, exclusão de conta, segurança de dados (Data safety) e demais requisitos da Google Play. Confirmar regras de uso para prestadores de serviço e retenção de dados conforme a LGPD.

**Segurança**: o app não inclui credenciais de serviço nem dados fictícios; o token de acesso à nuvem fica criptografado pelo Android Keystore. O backend usa OTP de curta duração, sessão expirável, conferência de compra na API do Google Play e AES-GCM para guardar backups. Links de aprovação são aleatórios, expiram em 14 dias e aceitam resposta uma vez. O aceite é registro comercial eletrônico, não assinatura certificada nem emissão de nota fiscal.

**Limites declarados**:
- O navegador apresenta consulta do histórico, não edição colaborativa simultânea.
- A nuvem guarda snapshots com controle de revisão. A restauração requer confirmação e substitui os dados locais após backup preventivo. Não é uma sincronização multiusuário em tempo real.
- Envio automático de cópia acontece após salvar orçamento e periodicamente quando o plano está confirmado e a conta ligada. Conflitos não são sobrescritos.
- O WorkManager dá avisos aproximados, sujeitos às permissões e economia de bateria do Android. Não usamos alarmes exatos.
- O sistema NÃO cobra clientes por PIX, não verifica pagamentos bancários, não é nota fiscal e não tem controle fiscal oficial.
- O custo do servidor, banco, SMTP e manutenção precisará ser avaliado antes de abrir o plano Online ao público.

## Fluxo de teste na loja

Verificar no celular: Histórico -> Novo orçamento -> Calcular preço por profissão -> salvar -> registrar custos -> criar OS -> agendar retorno -> compartilhar link de aprovação -> cliente responde -> importar resposta -> registrar pagamento/recibo -> exportar e restaurar backup. Depois, testar acesso Gratuito e os dois produtos pagos separadamente.

Nenhuma venda está garantida; a meta de R$ 10 mil/mês depende de aquisição, conversão, retenção, suporte e custos operacionais.

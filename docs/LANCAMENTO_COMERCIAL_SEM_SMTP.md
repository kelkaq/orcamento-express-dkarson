# Orçamento Express DKARSON — acesso online sem custo de e-mail

## Estado real da infraestrutura (08/11/2026)
- Projeto Supabase Free em São Paulo: `hajxpqnmeabyvfctlygp`.
- API HTTPS online: https://hajxpqnmeabyvfctlygp.supabase.co/functions/v1/orcamento-api
- Acesso à nuvem sem e-mail obrigatório, sem SMTP, sem OAuth ou domínio próprio.
- Criação e recuperação com chave aleatória de 256 bits; hash SHA-256 guardado em banco privado; sessões de 30 dias.
- O usuário recebe a chave uma única vez e deve guardá-la. Se a perder sem sessão ativa, não há recuperação administrativa sem comprovação adicional.
- O backend guarda backups cifrados com chave AES-256-GCM em tabela de acesso privilegiado, gerada aleatoriamente uma única vez. **Conservar a tabela `oe_crypto_keys` em qualquer restauração do banco**; perdê-la impossibilita descriptografar os backups.
- A central web permite criar/reabrir a conta, consultar dados apenas com Pro Online validado e excluir os próprios dados.
- Testes automáticos usam conta descartável, validam chave, troca, bloqueio do plano, exclusão e limpeza; não gravam tokens nos logs.

## Acesso com custo inicial zero
- Aplicativo funciona localmente sem cadastro.
- Conta online gratuita pode ser criada sem pagar por SMTP.
- Supabase Free tem cotas e não garante serviço contínuo para alta escala; reavaliar plano antes de ampliar a operação paga.
- Os serviços de e-mail existentes na API são legados/opcionais e não devem ser oferecidos para o público sem SMTP configurado.

## Custos e dependências que não são eliminados
- Publicar na Google Play exige **uma conta de desenvolvedor com taxa única de US$ 25**, caso ainda não exista. O custo é da Google, não do Supabase.
- Para vender Pro Offline ou assinatura Pro Online pela Play, é necessário cadastrar produtos e configurar verificação Android Publisher, notificações do Pub/Sub, conta comercial e testes oficiais de pagamento/reembolso.
- A Google Play aplica taxas de serviço sobre vendas e assinaturas conforme regras e elegibilidade.
- A versão **comercial assinada** exige chave de upload privada do titular (guardada fora do código), AAB de produção, dados reais de suporte e política de privacidade revisada.
- Sem Google Play Console configurada, o app pode funcionar para orçamento gratuito/local, mas **compras Pro não podem ser oferecidas como validadas**.
- O usuário não precisa pagar hospedagem nem e-mail **antecipadamente** enquanto operar dentro do Free. Cobranças por uso acima das cotas e plano comercial podem existir no futuro.

## Checklist final
- [x] Banco PostgreSQL, isolamento e RLS.
- [x] API Supabase HTTPS, URLs públicas e Central Online.
- [x] Criação de contas sem e-mail.
- [x] E2E de recuperação, revogação, exclusão de conta e acesso negado sem assinatura.
- [x] Implementações Google Play no aplicativo e no backend.
- [ ] Configurar Play Console real, cadastro de produtos e conta de serviço.
- [ ] Verificar renovação/cancelamento, compra única e reembolso com operações reais.
- [ ] Configurar chave de assinatura comercial, gerar AAB de release e instalar via Play.
- [ ] Completar ficha do aplicativo, políticas, suporte e distribuição conforme exigências do Google.
- [ ] Homologar manualmente em aparelhos Android físicos, incluindo backup, recuperação e compra.
- [ ] Revisar capacidade, disponibilidade, privacidade e suporte para clientes pagantes.

Não afirmar que os itens pendentes funcionam até serem validados na plataforma real.

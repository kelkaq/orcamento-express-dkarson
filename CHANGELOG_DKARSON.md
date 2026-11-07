# Registro da revisão do código recebido em 07/11/2026

## Implementado nesta rodada

1. Calculadora decimal: `util/CalculatorEngine.kt`, `ui/components/CalculatorDialog.kt`, integração em `QuoteEditorScreen.kt`.
2. Pesquisa local de clientes e catálogo no editor; múltipla adição de itens.
3. Campos numéricos de edição menos agressiva com a entrada parcial.
4. Dashboard reativo por consulta SQL no Room e nomenclatura correta: propostas aprovadas/concluídas, **não dinheiro recebido**.
5. Substituição de anúncio e Premium demonstrativos por identificação simples da DKARSON.
6. Exportação de backup mais segura; validação de dados obrigatórios, de integridade e gravação física antes de permitir substituir.
7. Testes unitários da calculadora; assinatura debug local configurada no Gradle.

## Verificação

- Compilação isolada do motor `CalculatorEngine.kt` com `kotlinc` (5 cenários matemáticos: passou).
- Verificação da consulta do dashboard com SQLite (executada nesta sessão).
- O projeto Android completo **não foi compilado ou instalado** por falta de Android SDK/Gradle.
- APK/AAB do ZIP original analisados como artefatos da versão anterior; não redistribuídos neste pacote.
- O AAB Release original tem assinatura de certificado **DKARSON**; o AAB genérico original usa **Android Debug**.

## Pendente

Os recursos maiores das fases A/B/C exigem iterações com migrações e testes Android: rascunhos persistentes, modelos, OS, pagamentos e recibos, gráficos por período, ditado por voz, notificações e monetização real. Não se deve cadastrar monetização falsa nem declarar que o aplicativo está pronto para a Play Console antes dessas validações.

**Segurança:** o ZIP original contém uma chave privada de upload (`my-upload-key.jks`). Este pacote não a inclui. Guarde-a em local privado e nunca envie para repositório público.

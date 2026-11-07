> **NOVO: Compilação automática pelo GitHub Actions** — consulte `PASSO_A_PASSO_COMPILAR_APK.md`. O aplicativo de teste é instalado ao lado da versão existente e não compartilha seu banco de dados. O projeto ainda não foi compilado após estas alterações.

# Orçamento Express — DKARSON

Aplicativo Android nativo em **Kotlin, Jetpack Compose e Room**, com dados locais.

## Continuação após exportação do Google AI Studio

Este pacote contém somente o **código-fonte** atualizado. Não inclui a chave de assinatura privada, APK ou AAB antigos. Os binários do ZIP original **não contêm as alterações deste pacote**.

### O que foi alterado nesta revisão

- Calculadora integrada com botão **Usar resultado** em preço unitário, desconto e frete/deslocamento.
- Entrada de números com estado local para não zerar enquanto a pessoa digita vírgula ou apaga parcialmente.
- Pesquisa instantânea de clientes e do catálogo; seleção de vários itens sem fechar a janela.
- Número de orçamento somente leitura no formulário.
- Métrica de propostas **aprovadas e concluídas**, reativa a alterações no Room, sem chamar isso de receita recebida.
- Remoção do anúncio fictício e do botão que ativava Premium sem compra validada.
- Validação mais defensiva de backups; snapshot transacional e `fsync` no backup preventivo.
- Uso da chave de debug local padrão do Android Gradle Plugin, sem depender de `debug.keystore` fixo.
- Testes unitários do motor da calculadora.

### Execução

1. Abra a pasta como projeto Android/Gradle num ambiente com **JDK, Android SDK e Gradle** compatíveis.
2. Execute os testes com `gradle testDebugUnitTest` ou pela IDE.
3. Compile um APK de **debug para teste** (`gradle assembleDebug`).
4. Faça um backup dos dados da versão instalada antes de testar qualquer atualização.
5. Para gerar o AAB de produção, use **sua chave privada de upload** em local seguro e as variáveis `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`. Nunca forneça essas senhas ao repositório nem ao chat.
6. Somente publique depois de testes em Android real e conferência dos requisitos da Play Console.

A versão de aplicativo instalada no telefone não muda até ser compilada e instalada uma nova versão assinada de forma compatível. Para atualizar sem apagar dados, o `applicationId` deve permanecer `com.dkarson.orcamentoexpress` e a assinatura deve ser compatível com a anterior.

### Limitações desta revisão

Não foi possível compilar um APK/AAB nesta sessão porque o ambiente não contém Android SDK/Gradle. O motor matemático foi compilado e exercitado separadamente com `kotlinc`, mas **a compilação Android e os testes de tela ainda precisam ocorrer**.

Os módulos de ordem de serviço, pagamentos/recibos, rascunhos persistidos, reconhecimento de voz, relatórios por período e compras Google Play Billing **ainda não foram implementados nesta revisão**. Eles exigem novos modelos de dados, migrações e testes em conjunto, sem arriscar os dados reais do usuário.

Para publicação, a política de privacidade precisa de revisão jurídica e de uma URL pública adequada; o texto mostrado dentro do aplicativo não substitui essa exigência.

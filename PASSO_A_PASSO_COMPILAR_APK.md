# ORÇAMENTO EXPRESS — COMO GERAR UM APK SEM GEMINI E SEM INSTALAR ANDROID SDK NO PC

## O que há neste ZIP

É o projeto Kotlin/Jetpack Compose/Room atualizado, já com o arquivo de automação `.github/workflows/compilar-apk.yml`.

**Este ZIP não é um APK; a automação ainda precisa ser executada no GitHub.**

## Caminho recomendado: GitHub Actions

1. Instale o **GitHub Desktop** (https://desktop.github.com/) e faça login na sua conta GitHub.
2. Extraia este ZIP em uma pasta no PC, por exemplo `C:\APPS\orcamento-express-dkarson`. Não copie só o ZIP: extraia todo o conteúdo e mantenha a pasta oculta `.github`.
3. No GitHub Desktop, use `File > Add local repository...` e selecione a pasta extraída. Se aparecer a opção **Create a repository here**, confirme.
4. Dê um nome como `orcamento-express-dkarson`, faça um commit dos arquivos e clique **Publish repository**. **Mantenha a opção Private / desmarque publicação pública.**
5. Abra o repositório no navegador, entre em **Actions** e procure o fluxo **Gerar APK de teste — DKARSON**. Ele também deve disparar automaticamente no primeiro envio do código.
6. Se necessário, clique **Run workflow**. Abra a execução e acompanhe os passos. Quando tudo compilar, baixe o arquivo **ORCAMENTO_EXPRESS_DKARSON_TESTE_APK** em **Artifacts**.
7. Extraia o APK do artefato baixado e envie ao celular. O aplicativo aparece como **Orçamento Express (Teste)**.

**Atenção:** use somente a versão teste para experimentos. O APK de teste possui identificador `com.dkarson.orcamentoexpress.teste`, separado da versão anterior instalada `com.dkarson.orcamentoexpress`. Não mexe no banco da versão anterior. A versão teste começa com banco vazio; se necessário, importe um backup compatível dentro dela, antes de testar a restauração.

## Por que o fluxo não assina AAB Release?

Porque a chave privada de upload não deve ficar no repositório. O fluxo gera apenas o **APK de teste** assinado automaticamente como DEBUG, sem nenhuma senha privada do usuário. O AAB Release será preparado em etapa separada usando a chave de upload protegida, depois de concluir os testes.

## Caso a compilação falhe

- No GitHub, abra **Actions > execução com erro > Compilar e testar Android**.
- Copie o **nome da etapa que falhou e as últimas linhas do erro** e envie no chat.
- Não substitua códigos por tentativas aleatórias e **não envie arquivos .jks, senhas ou tokens**.

Este fluxo foi **preparado e inspecionado estaticamente**, mas ainda não foi executado neste ambiente. A compilação real depende do primeiro GitHub Actions.

## Alternativa: Android Studio no Windows

1. Baixe em https://developer.android.com/studio .
2. Instale Android Studio e o Android SDK.
3. Abra a pasta extraída em `Open`.
4. Permita que o Android Studio sincronize Gradle e instale Android SDK Platform 36.1 e Build Tools 36.0.0.
5. Execute `Build > Build Bundle(s) / APK(s) > Build APK(s)` com a variante `debug`.
6. O APK ficará em `app/build/outputs/apk/debug/app-debug.apk`.

Na alternativa do Android Studio, pode ser necessário gerar os scripts oficiais do Gradle Wrapper; o repositório usa o Gradle 9.3.1, indicado em `gradle/wrapper/gradle-wrapper.properties`.

## Segurança

O projeto não inclui .jks, APK/AAB antigos, arquivo .env nem credenciais. Não adicione esses arquivos ao GitHub. A lista `.gitignore` bloqueia esses formatos. Nunca publique o repositório publicamente enquanto estiver fazendo testes.

#!/usr/bin/env bash
# CI: desabilita o downloader HTTPS interno do Robolectric/Conscrypt.
# Um único jar de Android 16 é obtido pelo curl da runner, salvo em cache e reutilizado.
set -euo pipefail
: "${RUNNER_TEMP:?Rodar somente no GitHub Actions}"
: "${GITHUB_ENV:?Rodar somente no GitHub Actions}"
version="16-robolectric-13921718-i7"
directory="${RUNNER_TEMP}/robolectric-android16"
jar="${directory}/android-all-instrumented-${version}.jar"
url="https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented/${version}/android-all-instrumented-${version}.jar"
mkdir -p "${directory}"
if [[ ! -s "${jar}" ]]; then
  echo "Preparando framework Android 16 para execução de testes..."
  curl --fail --show-error --silent --location \
    --retry 5 --retry-delay 5 --retry-all-errors \
    --connect-timeout 15 --max-time 240 \
    "${url}" --output "${jar}.tmp"
  mv "${jar}.tmp" "${jar}"
fi
test -s "${jar}"
# A JVM do worker de testes herda JAVA_TOOL_OPTIONS, evitando os downloads internos
# da dependência por Robolectric. O arquivo não é incluído no APK/AAB.
printf 'JAVA_TOOL_OPTIONS=-Drobolectric.dependency.dir=%s\n' "${directory}" >> "${GITHUB_ENV}"
echo "Framework Robolectric Android 16 pronto para testes offline."

# Build Android e APK

## Requisitos

- JDK 17 (o projeto configura bytecode Java/Kotlin 17).
- Android SDK Platform 36 e Build Tools 36.0.0.
- Internet na primeira compilação para baixar Gradle, plugins e dependências, incluindo o encoder LAME via JitPack.

O Gradle Wrapper 8.7 está incluído. Use `./gradlew` (Linux/macOS) ou `gradlew.bat` (Windows); o bit executável do wrapper está versionado.

## Build de desenvolvimento

```bash
./gradlew testDebugUnitTest assembleDebug
```

O APK de desenvolvimento é criado em `app/build/outputs/apk/debug/app-debug.apk`. O CI publica esse arquivo como artefato `MixTapeMp3-debug-apk` por 14 dias. Ele não é assinado para distribuição de release.

## Teste E2E

Com emulador/dispositivo conectado e depuração habilitada:

```bash
./gradlew connectedDebugAndroidTest
```

O teste instrumentado executa a Activity real, toca na chamada da tela de boas-vindas e confirma que a tela Home foi aberta. O workflow `Android E2E` executa o mesmo teste em emulador API 35. Consulte [`VALIDACAO_CI.md`](VALIDACAO_CI.md).

## Release assinado

Crie `keystore.properties` localmente com os campos `storeFile`, `storePassword`, `keyAlias` e `keyPassword`; mantenha também o arquivo keystore fora do Git. Depois execute:

```bash
./gradlew assembleRelease
```

O artefato fica em `app/build/outputs/apk/release/`. Sem a configuração de assinatura, o release não será assinado. Não comite senhas, keystores nem `keystore.properties`.

## Histórico da falha corrigida

A primeira execução de validação falhou durante a compilação do script Gradle Kotlin DSL (`app/build.gradle.kts`): `java.util.Properties` era interpretado em conflito com o accessor `java` do DSL, e a chamada `load` não era resolvida. Foi corrigido importando `java.util.Properties` explicitamente e carregando o arquivo com a instância `Properties`. Antes disso, o wrapper também não tinha permissão de execução no ZIP original; a permissão executável foi restaurada e versionada. A execução corrigida e os resultados das Actions são registrados em [`VALIDACAO_CI.md`](VALIDACAO_CI.md).

## Pontos de validação de release

- Confirmar que JitPack resolve `com.github.naman14:TAndroidLame:1.1` e inspecionar o encoder incluído no APK.
- Validar o fluxo funcional completo em dispositivo: importar áudio, analisar, equalizar, montar CD, gerar MP3, reproduzir e exportar.
- Verificar alinhamento nativo para páginas de 16 KB antes de distribuir o release.
- O app usa `compileSdk` e `targetSdk` 36; validar edge-to-edge e o limite de duração do foreground service `dataSync` em um aparelho/API de destino.

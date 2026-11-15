# MixTape MP3

Aplicativo Android offline-first para importar, normalizar, equalizar e montar CDs digitais em um único MP3 (até 320 kbps).

## Build e validação

- Requisitos locais: JDK 17, Android SDK Platform 36 e conexão à internet para baixar as dependências na primeira compilação.
- APK de desenvolvimento: `./gradlew assembleDebug` — arquivo em `app/build/outputs/apk/debug/app-debug.apk`.
- Testes unitários: `./gradlew testDebugUnitTest`.
- Teste E2E instrumentado: `./gradlew connectedDebugAndroidTest` com emulador/dispositivo Android conectado.
- O GitHub Actions executa o build/teste e o E2E em fluxos separados a cada push para `main` ou pull request. O APK debug fica no artefato `MixTapeMp3-debug-apk` da execução de CI.

O APK debug é adequado para instalação de teste e não é assinado para distribuição. Para release assinado, consulte [`docs/BUILD.md`](docs/BUILD.md) e mantenha o keystore fora do repositório. O histórico da correção e a cobertura do E2E estão em [`docs/VALIDACAO_CI.md`](docs/VALIDACAO_CI.md).

Documentação do projeto: [`docs/ROADMAP.md`](docs/ROADMAP.md), [`docs/PROGRESSO.md`](docs/PROGRESSO.md), [`docs/CHANGELOG.md`](docs/CHANGELOG.md) e [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md).

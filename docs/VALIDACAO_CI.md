# Validação contínua e correções de build

## Problemas encontrados e corrigidos

A primeira execução local de `./gradlew testDebugUnitTest assembleDebug --stacktrace` falhou na configuração do módulo `app`. O Kotlin DSL reportou `Unresolved reference: util` em `java.util.Properties()` e `Unresolved reference: load` ao carregar `keystore.properties`. A referência `java` colidia com o accessor `java` do Gradle Kotlin DSL. `app/build.gradle.kts` agora importa explicitamente `java.util.Properties` e lê o arquivo com `Properties()`.

O ZIP também não preservava a permissão executável do wrapper `gradlew`. A permissão foi restaurada e versionada.

A primeira execução das Actions falhou porque `android-actions/setup-android@v3` solicitava o pacote `tools`, que deixou de ser distribuído. Os workflows agora usam `android-actions/setup-android@v4.0.4`, pedem apenas `platform-tools` e instalam Android SDK Platform 36/Build Tools 36.0.0 com `sdkmanager`. Referências: [README da action setup-android](https://github.com/android-actions/setup-android) e [notas de versão](https://github.com/android-actions/setup-android/releases).

Depois de o SDK ser instalado, o manifest merger revelou outro conflito: o app define `android:allowBackup="false"`, enquanto o manifesto transitivo de `TAndroidLame:1.1` define `true`. O app mantém backups desativados e declara `tools:replace="android:allowBackup"` explicitamente no elemento `<application>`.

## Testes adicionados

- `WelcomeFlowTest` inicia a `MainActivity`, toca em “Toque para começar” e verifica que o botão “Escolher pasta de saída” da Home foi exibido.
- O workflow **Android CI** (`.github/workflows/android-ci.yml`) executa `testDebugUnitTest` e `assembleDebug`, depois publica o APK debug.
- O workflow **Android E2E** (`.github/workflows/android-e2e.yml`) executa `connectedDebugAndroidTest` em emulador Android API 35 e publica o relatório mesmo se o teste falhar.

Ambos os workflows executam em push para `main`, pull request e manualmente pela aba Actions (`workflow_dispatch`). O APK debug sai como artefato `MixTapeMp3-debug-apk`, com retenção de 14 dias; os resultados E2E são publicados em `MixTapeMp3-e2e-results`. O teste E2E cobre inicialização e navegação inicial; não substitui validação manual de importação, codificação MP3, reprodução e exportação.

## Resultados das execuções

Os logs e a conclusão atualizados devem ser consultados na aba [GitHub Actions](https://github.com/Kyra2214/MixTapeMp3/actions). A validação final após as correções do manifest deve confirmar a criação do APK e a passagem do E2E.


## Conflito de dependências do encoder

Após resolver o manifesto, `checkDebugDuplicateClasses` detectou classes `android.support.v4.*` duplicadas entre `androidx.core:core:1.13.1` e a biblioteca antiga `com.android.support:support-compat:26.1.0`, trazida pelo POM do `TAndroidLame`. O wrapper do encoder usa somente `AndroidLame`/`LameBuilder`, sem AppCompat. A dependência agora exclui `com.android.support`; `./gradlew :app:dependencies --configuration debugRuntimeClasspath` confirmou que a árvore antiga não é mais resolvida.

## Inicialização do emulador E2E

O log do runner mostrou que `/dev/kvm` não estava acessível e que o emulador estava sendo lançado com aceleração Linux desativada; ele nunca alcançou `sys.boot_completed`. O workflow agora aplica a regra udev recomendada pelo GitHub/Android Emulator Runner para permitir KVM e configura `disable-linux-hw-accel: false`. O novo run precisa validar o boot real e o teste Compose.

## Teste instrumentado e artefato CI

No run `43754664898`, o workflow Android CI concluiu com sucesso e publicou o APK debug. No E2E, a regra KVM permitiu iniciar o emulador; a compilação instrumentada então apontou que `assertExists` não está disponível na versão Compose UI Test usada. O teste foi ajustado para `assertIsDisplayed()` e será reexecutado para validar a navegação até a Home.

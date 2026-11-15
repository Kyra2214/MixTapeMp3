

## Conflito do manifesto e correção

Depois de o SDK ser instalado corretamente, o build revelou um conflito no manifest merger: o app define `android:allowBackup="false"`, enquanto o manifesto transitivo de `TAndroidLame:1.1` define o atributo como `true`. O aplicativo opta por manter o backup desativado e agora declara `tools:replace="android:allowBackup"` no elemento `<application>`, tornando explícita a precedência do manifesto do app. Os workflows precisam ser executados novamente para confirmar a compilação e o teste E2E após a correção.

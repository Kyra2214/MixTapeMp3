# ROADMAP DE CORREÇÕES — CD Virtual 1.0 (v076 / 10.8-prep)

Gerado em 15/11/2026 por leitura estática do ZIP `CDVirtual_v076_10.8-prep.zip` (83 arquivos `.kt`, ~5.065 linhas). Nada foi compilado, executado ou alterado. Não substitui `docs/ROADMAP.md` (requisitos originais). Todas as tarefas começam **PENDENTE**.

## Avisos antes de executar

1. **`audit/data.py` e `pkgdeps.json` não foram enviados.** As pendências abaixo foram **reverificadas no código** a partir dos IDs que o `gen.py` cita: P-001…P-017, P-025, P-030, P-031, P-032, P-035. Os IDs **P-018…P-024, P-026…P-029, P-033, P-034** não aparecem no `gen.py` e não foram reconstruídos. Se existirem no registro original, precisam de tarefa própria.
2. Achei **1 defeito novo** que não consta no `gen.py`; ele usa ID **N-01** para não colidir com a numeração original.
3. A numeração T-xxx segue o que o `gen.py` já sugere (T-001 decisões; T-101 encoder; T-102 wrapper; T-103 migração; T-304 portão de T-001; T-501/T-502 rolagem; T-702 checklist manual). As demais foram atribuídas aqui. Se você regenerar os outros arquivos, os números fixos no texto do `gen.py` (ex.: "14 independentes") precisam ser atualizados.
4. Coordenadas e API da biblioteca LAME **não puderam ser confirmadas** (sem acesso ao repositório). Continua RISCO até o primeiro build.

## Status de execução

Último ZIP: CDVirtual_CHECKPOINT_023.zip · Progresso: 92.3% (24/26 tarefas resolvidas: CONCLUÍDA, ENCERRADA ou IMPLEMENTADA). Denominador corrigido: eram 24 tarefas + T-205 (a conta anterior 17/30 usava denominador incorreto).

| Tarefa | Status |
|---|---|
| T-001 | CONCLUÍDA (decisões do usuário em 15/11/2026) |
| T-101 | IMPLEMENTADA (aguardando validação) |
| T-102 | IMPLEMENTADA (aguardando validação) |
| T-103 | IMPLEMENTADA (aguardando validação) |
| T-201 | IMPLEMENTADA (aguardando validação) |
| T-202 | IMPLEMENTADA (aguardando validação) |
| T-203 | IMPLEMENTADA (aguardando validação) |
| T-204 | IMPLEMENTADA (aguardando validação) |
| T-205 | IMPLEMENTADA (aguardando validação) |
| T-301 | IMPLEMENTADA (aguardando validação) |
| T-302 | IMPLEMENTADA (aguardando validação) |
| T-303 | IMPLEMENTADA (aguardando validação) |
| T-304 | ENCERRADA (P-015 não exigido, decisão do usuário em 15/11/2026) |
| T-401 | IMPLEMENTADA (aguardando validação) |
| T-402 | IMPLEMENTADA (aguardando validação) |
| T-501 | IMPLEMENTADA (aguardando validação) |
| T-502 | IMPLEMENTADA (aguardando validação) |
| T-503 | IMPLEMENTADA (aguardando validação) |
| T-504 | IMPLEMENTADA (aguardando validação) |
| T-505 | IMPLEMENTADA (aguardando validação) |
| T-601 | IMPLEMENTADA (aguardando validação) |
| T-602 | IMPLEMENTADA (aguardando validação) |
| T-701 | IMPLEMENTADA (aguardando validação) |
| T-702 | IMPLEMENTADA (checklist entregue; execução pelo usuário) |
| T-801 | IMPLEMENTADA (aguardando validação) — pedido do usuário, fora do Roadmap original |
| T-703 | PENDENTE |

## Regras para o agente executor

1. Executar apenas as tarefas deste documento; não criar funcionalidades fora dos requisitos originais.
2. Respeitar *Dependências obrigatórias* e a ordem de *Coordenação* (arquivos compartilhados).
3. Preservar a arquitetura (módulo único, pacotes atuais, interface `Mp3Encoder`) e a política de originais somente leitura.
4. **Nenhuma tarefa altera o schema do Room** (registros novos usam arquivos/SharedPreferences). Se alguma exigir isso, parar e registrar em T-001.
5. Sem SDK/rede/dispositivo, não afirmar que algo funciona: marcar **IMPLEMENTADA (aguardando validação)**. Só o usuário marca **CONCLUÍDA**.
6. Estados: PENDENTE → EM ANDAMENTO → IMPLEMENTADA (aguardando validação) → CONCLUÍDA | BLOQUEADA | ENCERRADA (com justificativa).
7. Registrar cada correção em `docs/CHANGELOG.md`.
8. Nunca versionar `keystore.properties`.

## Visão geral

| Fase | Tarefas | Pendências |
|---|---|---|
| 0 — Decisões | T-001 | P-015, P-030, P-031, P-032 |
| 1 — Build e infraestrutura | T-101, T-102, T-103 | P-001, P-002, P-003 |
| 2 — Armazenamento e biblioteca | T-201, T-202, T-203, T-204, T-205 | P-016, P-013, P-011, N-01, P-030 |
| 3 — Análise, EQ e importador | T-301, T-302, T-303, T-304 | P-014, P-017, P-006, P-015 |
| 4 — Geração e exportação | T-401, T-402 | P-012, P-010 |
| 5 — Telas e player | T-501, T-502, T-503, T-504, T-505 | P-004, P-005, P-007, P-008, P-009 |
| 6 — Qualidade e conformidade | T-601, T-602 | P-025, P-031 |
| 7 — Fecho | T-701, T-702, T-703 | — |

Total: **26 tarefas** (24 originais + T-205, criada por P-030, + T-801, pedido do usuário). Prioridades: ALTA 9 (T-101, T-102, T-103, T-303, T-501, T-502, T-703, T-001, T-701), MÉDIA 13, BAIXA 2 (T-601, T-602).

## Ondas de execução

| Onda | Tarefas | Observação |
|---|---|---|
| 0 | T-001 | Pode rodar em paralelo com a Onda 1; só T-304 e T-602 dependem dela. |
| 1 | T-101, T-102, T-103, T-201, T-202, T-203, T-204, T-301, T-302, T-303, T-401, T-402, T-501, T-502, T-503, T-504, T-505 | Sem dependência obrigatória. Seguir a ordem de coordenação abaixo. |
| 2 | T-304, T-601, T-602 | Dependem de T-001 e/ou do fim das tarefas que mexem nos mesmos arquivos. |
| 3 | T-701, T-702 | Documentação e checklist. |
| 4 | T-703 | APK final, validado pelo usuário. |

**Ordem de coordenação (arquivos compartilhados):**
- `TrackImporter.kt` / `StorageAccess.kt` / `LibraryViewModel.kt`: T-204 → T-201 → T-203 → T-202
- `Mp3Player.kt`: T-504 → T-505
- `CdEditorScreen.kt` / `PlayerScreen.kt` / `ExportScreen.kt`: T-501 e T-502 → T-503 → T-402

## Dependências obrigatórias

| Tarefa | Depende de |
|---|---|
| T-304 | T-001 |
| T-602 | T-001, T-101 |
| T-601 | T-201, T-202, T-203, T-401, T-402, T-503 |
| T-701 | todas as T-1xx a T-6xx |
| T-702 | T-701 |
| T-703 | T-101, T-102, T-103, T-702 |
| demais | nenhuma |

---

## FASE 0 — DECISÕES

### T-001 — Decisões que dependem do Roadmap original
- **DECIDIDO (usuário, 15/11/2026):** P-015 não exigido → T-304 ENCERRADA; P-030 exigido → T-205; P-031 distribuição pela Google Play → T-602 sobe para API 36; P-032 EQ/ganho por faixa (como está, sem schema).
- **Pendências:** P-015, P-030, P-031, P-032 · **Prioridade:** ALTA
- **Objetivo:** fechar as 4 dúvidas que o resumo `docs/ROADMAP.md` não responde.
- **Ações:**
  1. Enviar o Roadmap original completo (ou responder as perguntas abaixo).
  2. P-015: o requisito 3.4 exige identificar bitrate/qualidade em links diretos? Hoje `LinkAnalyzer.directOption` devolve uma única opção com `bitrateKbps = null`.
  3. P-030: excluir projeto de CD é exigido? Hoje `CdDao` não tem delete e a tela não oferece exclusão.
  4. P-032: EQ/ganho valem **por faixa** (como está) ou **por CD**? Por CD exigiria alterar o schema (viola a regra 4).
  5. P-031: elevar `targetSdk`? **Fato verificado:** a página oficial do Google Play exige API 35 desde 31/08/2025 e **API 36 desde 31/08/2026** (extensão possível até 01/11/2026). Se o app for distribuído só por APK manual, não há obrigação; se for pela Play, o alvo é 36, não 35.
- **Critérios:**
  - [ ] Cada decisão registrada com data e fonte.
  - [ ] T-304 e T-602 liberadas ou encerradas.

---

## FASE 1 — BUILD E INFRAESTRUTURA

### T-101 — Validar e isolar o encoder LAME (P-001, CRÍTICA, RISCO)
- **Arquivos:** `app/build.gradle.kts`, `settings.gradle.kts`, `media/encoder/LameMp3Encoder.kt`
- **Evidência:** dependência `com.github.naman14:TAndroidLame:1.1` via JitPack com filtro `includeGroup("com.github.naman14")`; `LameMp3Encoder` usa `LameBuilder`/`AndroidLame` conforme comentário "ASSUMINDO".
- **Ações:**
  1. No primeiro build com internet, confirmar que a coordenada resolve (se falhar, ajustar `group`/versão e o filtro `includeGroup`).
  2. Confirmar assinaturas de `encode(short[], short[], int, byte[])`, `flush(byte[])`, `close()`.
  3. Tornar o CBR explícito no builder (modo estéreo e VBR desligado), se a API oferecer, em vez de depender do padrão.
  4. Conferir no AAR as ABIs nativas (`arm64-v8a` obrigatório em aparelhos atuais).
  5. Se algo falhar, trocar **só** `LameMp3Encoder.kt` (a interface `Mp3Encoder` e o motor não mudam).
- **Critérios:**
  - [ ] Projeto compila e resolve a dependência.
  - [ ] MP3 de teste passa em `Mp3Validator` (CBR 320, 44,1 kHz).
  - [ ] `.so` de arm64-v8a presente no APK.

### T-102 — Gradle wrapper (P-002, ALTA)
- **Arquivos:** `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `docs/BUILD.md`, `.gitignore`
- **Ações:**
  1. Gerar com `gradle wrapper --gradle-version <versão exigida pelo AGP 8.5.2>` (confirmar a versão mínima na tabela de compatibilidade do AGP; a referência é Gradle 8.7) e JDK 17.
  2. Versionar `gradle-wrapper.jar` (verificar que `.gitignore` não o exclui).
  3. Atualizar `docs/BUILD.md` para `./gradlew assembleDebug`.
- **Critérios:**
  - [ ] `./gradlew assembleDebug` roda a partir de clone limpo.
- **Observação:** o `.jar` é binário e não pode ser escrito offline; precisa ser gerado em máquina com rede.

### T-103 — Migrações reais do Room (P-003, ALTA)
- **Arquivos:** `core/database/AppDatabase.kt`, `app/build.gradle.kts`, `app/schemas/`
- **Evidência:** `fallbackToDestructiveMigration()` ativo na v10; `app/schemas` vazio, embora `exportSchema = true` e `room.schemaLocation` estejam configurados.
- **Ações:**
  1. Tratar a v10 como baseline da 1.0: no build, o KSP gera `schemas/.../10.json`; versioná-lo.
  2. Trocar por `fallbackToDestructiveMigrationFrom(1..9)` apenas para builds de desenvolvimento antigos; v10 em diante nunca destrói dados.
  3. Documentar a regra: toda mudança de schema pós-1.0 exige `Migration` + incremento de versão.
  4. (Opcional) teste de migração com `room-testing`.
- **Critérios:**
  - [ ] `10.json` versionado.
  - [ ] Nenhuma chamada a `fallbackToDestructiveMigration()` sem argumento de versões.
  - [ ] Schema inalterado (sem novas colunas).
- **Observação:** o 10.json só existe depois de um build; marcar IMPLEMENTADA até o usuário gerar.

---

## FASE 2 — ARMAZENAMENTO E BIBLIOTECA

### T-201 — Limite de permissões URI persistidas (P-016, MÉDIA, RISCO)
- **Arquivos:** `feature/library/TrackImporter.kt`, `core/storage/StorageAccess.kt`, `feature/library/LibraryViewModel.kt`
- **Evidência:** `takePersistableUriPermission` por arquivo (linha `if (uri.scheme == "content") storage.persistRead(uri)`), chamado **antes** de `AudioMetadataReader.read` (importação que falha deixa permissão presa) e **nunca liberado** ao excluir da biblioteca (`releasePersistableUriPermission` só existe em `OutputFolder`).
- **Ações:**
  1. Persistir a permissão só após a leitura de metadados dar certo.
  2. Ao remover a faixa, liberar a permissão do URI (se `content://`).
  3. Se `persistedUriPermissions.size` se aproximar do limite da plataforma (conferir o valor atual na documentação do Android; historicamente 128 em versões antigas e 512 nas atuais), avisar o usuário antes de falhar.
  4. Tratar `SecurityException` ao persistir como `ImportOutcome.Failed` com mensagem clara.
- **Critérios:**
  - [ ] Excluir faixa libera a permissão.
  - [ ] Falha de importação não deixa permissão presa.
  - [ ] Importar lote grande não falha silenciosamente.

### T-202 — Downloads órfãos em `filesDir/imports` (P-013, MÉDIA)
- **Arquivos:** `feature/library/LibraryViewModel.kt`, `media/connectors/ImportRecovery.kt`
- **Evidência:** faixas importadas por link têm `uri = file://…/imports/<ts>.<ext>`; `LibraryViewModel.remove` apaga só registro e capa, deixando o arquivo baixado.
- **Ações:**
  1. Ao remover faixa cujo URI esteja dentro de `filesDir/imports`, apagar o arquivo (é cópia do app, não original do usuário).
  2. Na abertura do app, varrer `imports/` e apagar arquivos sem faixa correspondente (`getByUri`).
  3. Nunca apagar arquivos fora de `imports/`.
- **Critérios:**
  - [ ] Excluir faixa baixada libera o espaço.
  - [ ] Original do usuário (`content://`) jamais é tocado.

### T-203 — Pasta de destino duplicada (P-011, MÉDIA)
- **Arquivos:** `feature/home/HomeScreen.kt`, `core/storage/OutputFolder.kt`, `core/storage/StorageAccess.kt`
- **Evidência:** a `HomeScreen` grava `storage.outputFolder` direto e chama `persistReadWrite`, sem validar nem liberar a pasta anterior; a `ExportScreen` usa `OutputFolder.choose`, que valida e libera.
- **Ações:** usar `OutputFolder` também na Home (mesmo estado, mesmas regras); remover a lógica própria.
- **Critérios:**
  - [ ] Escolher pasta na Home e na Exportar produz o mesmo estado e libera a pasta antiga.

### T-204 — Importação por link fora da thread principal (N-01, MÉDIA, NOVO)
- **Arquivos:** `feature/importer/ImporterViewModel.kt`, `feature/library/TrackImporter.kt`
- **Evidência:** `importSelected` roda em `viewModelScope` (Main) e chama `trackImporter.import(...)`, cujo corpo faz `sha256Of` (leitura do arquivo inteiro) e `MediaMetadataRetriever` **sem trocar de dispatcher**. A `LibraryViewModel` já usa `Dispatchers.IO` por isso.
- **Ações:** envolver o corpo de `TrackImporter.import` em `withContext(Dispatchers.IO)`.
- **Critérios:**
  - [ ] Importar arquivo grande por link não congela a tela (verificar com StrictMode).

### T-205 — Excluir projeto de CD (P-030, MÉDIA, NOVA)
- **Arquivos:** `core/database/CdDao.kt`, `feature/cdeditor/CdRepository.kt`, `feature/cdeditor/CdEditorViewModel.kt`, `feature/cdeditor/CdEditorScreen.kt`
- **Evidência:** `CdDao` sem delete; a tela não oferece exclusão. `cd_tracks` já tem `onDelete = CASCADE`, então **não há mudança de schema**.
- **Ações:** `CdDao.delete(id)`; `CdRepository.delete` apaga o CD e a capa se ninguém mais a usa; botão "Excluir CD" com confirmação; limpar a seleção. Não apaga músicas da biblioteca, originais nem MP3 já gerados.
- **Critérios:**
  - [ ] Excluir CD remove só o projeto (faixas e originais intactos).
  - [ ] Capa órfã é apagada; capa compartilhada permanece.

---

## FASE 3 — ANÁLISE, EQ E IMPORTADOR

### T-301 — Falha de análise visível (P-014, MÉDIA)
- **Arquivos:** `feature/library/AnalysisRunner.kt`, `feature/library/LibraryScreen.kt`
- **Evidência:** `catch (e: Throwable) { failed += t.id }` descarta o erro; a tela mostra "Analisando volume…" para sempre quando `lufs == null`.
- **Ações:**
  1. Expor, em memória (`StateFlow<Map<Long,String>>`), as faixas que falharam e o motivo (sem coluna nova).
  2. Mostrar "Falha na análise: motivo" e botão "Tentar de novo" (remove do conjunto e chama `kick()`).
- **Critérios:**
  - [ ] Arquivo corrompido deixa de aparecer como "Analisando…".

### T-302 — Alocação por amostra na análise (P-017, MÉDIA)
- **Arquivo:** `media/normalization/LoudnessAnalyzer.kt`
- **Evidência:** `trackTruePeak` cria `floatArrayOf(0.25f, 0.5f, 0.75f)` a cada amostra de cada canal.
- **Ações:** mover para constante do `object`; manter a matemática idêntica.
- **Critérios:**
  - [ ] Resultados numéricos inalterados (mesmo `truePeakDb` em arquivo de teste).
  - [ ] Nenhuma alocação no laço de amostras.

### T-303 — EQ automática do CD, não da biblioteca (P-006, ALTA)
- **Arquivos:** `feature/equalizer/EqualizerViewModel.kt`, `feature/equalizer/EqualizerScreen.kt`
- **Evidência:** `autoEqualizeAll()` aplica `AutoEqPlanner.plan(tracks.value)` à **biblioteca inteira** (premissa antiga "ASSUMINDO"), embora o editor de CD exista desde a Fase 05; `resetEqAll` idem.
- **Ações:**
  1. Adicionar à tela um seletor do CD (reaproveitar a preferência `cdeditor/selected` e `CdRepository.cds`).
  2. `autoEqualizeCd(cdId)` usa `cdRepository.tracksOf(cdId).first()`; "Zerar EQ" também só no CD.
  3. Sem CD selecionado: rótulo explícito "biblioteca inteira".
  4. Mantém o armazenamento por faixa (schema inalterado). Aviso: faixa em dois CDs compartilha a EQ; se P-032 exigir EQ por CD, abrir tarefa fora da regra 4.
- **Critérios:**
  - [ ] Faixas fora do CD escolhido não mudam.
  - [ ] Originais intactos.

### T-304 — Formatos e qualidades em links diretos (P-015, MÉDIA)
- **Arquivos:** `media/connectors/LinkAnalyzer.kt`, `feature/importer/ImporterScreen.kt`
- **Dependência:** T-001
- **Ações (só se T-001 confirmar a exigência):** estimar bitrate por `tamanho / duração` quando houver duração e marcar como estimado; **não presumir** bitrate sem dado. Se T-001 disser que não é exigido: ENCERRADA com justificativa.
- **Critérios:**
  - [ ] Decisão de T-001 aplicada ou tarefa encerrada.

---

## FASE 4 — GERAÇÃO E EXPORTAÇÃO

### T-401 — Colisão de nomes de MP3 (P-012, MÉDIA)
- **Arquivos:** `media/service/ExportService.kt`, `feature/export/GeneratedCds.kt`
- **Evidência:** `target = exports/<safeFileName(nome)>.mp3`; dois CDs com o mesmo nome (ou renomear um CD para nome já usado) **sobrescrevem** o MP3 anterior (`Mp3Exporter` apaga o destino existente).
- **Ações:** nome único: `<nome>.mp3` se livre ou se pertencer ao mesmo CD; senão `<nome> (2).mp3`. Para reconhecer "mesmo CD", gravar o id na tag (ex.: `TXXX`) ou usar sufixo estável. Não alterar schema.
- **Critérios:**
  - [ ] Dois CDs com mesmo nome geram dois arquivos.
  - [ ] Regerar o mesmo CD continua substituindo o seu arquivo.

### T-402 — Cópias exportadas só em memória (P-010, MÉDIA)
- **Arquivos:** `feature/export/ExportScreen.kt`
- **Evidência:** `exportedNames` e `exportedDocs` são `remember { mutableMapOf() }`: somem ao sair da tela/processo, perdendo "excluir também as cópias" e o nome correto no CUE.
- **Ações:** persistir o mapa (arquivo JSON em `filesDir` ou SharedPreferences `export_copies`); limpar entradas ao excluir.
- **Critérios:**
  - [ ] Reabrir o app mantém a opção de excluir cópias.
  - [ ] CUE exportado depois aponta para o nome real do MP3 na pasta.

---

## FASE 5 — TELAS E PLAYER

### T-501 — Editor de CD sem rolagem (P-004, ALTA)
- **Arquivo:** `feature/cdeditor/CdEditorScreen.kt`
- **Evidência:** `Column(fillMaxSize)` com `CdDetails` (lista de faixas via `forEachIndexed`, botões) acima de um `LazyColumn`; sem `verticalScroll`. Com CD longo, "Gerar MP3" e a lista de CDs ficam inacessíveis.
- **Ações:** transformar a tela em um único `LazyColumn` (detalhes, faixas e lista de CDs como itens) ou usar `verticalScroll` sem `LazyColumn` aninhado.
- **Critérios:**
  - [ ] CD com 100 faixas: todos os botões alcançáveis.
  - [ ] Rotação preserva o estado.

### T-502 — Player sem rolagem (P-005, ALTA)
- **Arquivo:** `feature/player/PlayerScreen.kt`
- **Evidência:** `Column(fillMaxSize)` com controles, capítulos (`forEachIndexed`) e um `LazyColumn` de MP3s; não rola.
- **Ações:** mesma abordagem de T-501; manter controles visíveis no topo.
- **Critérios:**
  - [ ] Muitos capítulos e muitos MP3s: tudo acessível.

### T-503 — Permissão de notificação para o player (P-007, MÉDIA)
- **Arquivos:** `feature/player/PlayerScreen.kt`, `feature/export/ExportScreen.kt`, `feature/cdeditor/CdEditorScreen.kt`
- **Evidência:** `POST_NOTIFICATIONS` só é pedida em "Gerar MP3"; "Reproduzir"/"Ouvir" iniciam o `PlaybackService` sem pedir, e no Android 13+ a notificação com controles não aparece.
- **Ações:** extrair o pedido para um helper compartilhado e usá-lo antes de tocar; tocar mesmo se negado.
- **Critérios:**
  - [ ] Primeiro play no Android 13+ pede a permissão; negar não impede a reprodução.

### T-504 — `prepare()` e leitura de tags na thread de UI (P-008, MÉDIA)
- **Arquivo:** `media/playback/Mp3Player.kt`
- **Evidência:** `play()` (síncrono, chamado de `onClick`) faz `player.prepare()` e `Id3Reader.readChapters(file)`.
- **Ações:** `prepareAsync()` com listener, e ler capítulos em `Dispatchers.IO`, publicando o estado ao terminar; manter `@Synchronized` coerente.
- **Critérios:**
  - [ ] Tocar MP3 grande não bloqueia a UI.
  - [ ] Erros continuam indo para `PlayerState.error`.

### T-505 — Foco de áudio ao retomar (P-009, MÉDIA)
- **Arquivos:** `media/playback/Mp3Player.kt`, `media/playback/PlaybackService.kt`
- **Evidência:** `requestFocus()` só roda em `onStartCommand` e `MediaSession.onPlay`; `resume()` pelo botão da tela ou pela notificação não repede o foco, então depois de `AUDIOFOCUS_LOSS` o MP3 volta a tocar por cima de outro app.
- **Ações:** centralizar o pedido/abandono de foco no caminho único de retomada (`Mp3Player.resume`/`togglePlayPause`), mantendo o tratamento de ducking e perdas.
- **Critérios:**
  - [ ] Retomar após ligação ou outro app pede foco novamente.

---

## FASE 6 — QUALIDADE E CONFORMIDADE

### T-601 — Código morto e duplicações (P-025, BAIXA)
- **Dependências:** T-201, T-202, T-203, T-401, T-402, T-503
- **Verificado por busca:** sem uso: `runCatchingResult`, `StorageAccess.hasPersistedAccess`, `Id3Reader.readText/parseCover/parseChapters/readTag`, `TrackRepository.update/findById`. Duplicações: `formatBytes` (ExportScreen) × `formatSize` (TimeFormat); decodificação amostrada de capa em `CoverImage`, `PlayerScreen` e `PlaybackService`; caminho `filesDir/exports` em `GeneratedCds`, `PlayerScreen`, `ExportService`, `ExportRecovery`.
- **Ações:** remover o morto; usar `formatSize`; centralizar `exports` em `ExportRecovery.exportsDir`; unificar decodificação de capa. Fazer **depois** das tarefas que editam esses arquivos. P-035 (ciclo de pacotes via `CDVirtualApp`) fica como OBS: não corrigir.
- **Critérios:**
  - [ ] Nenhuma mudança de comportamento; build limpo.

### T-602 — targetSdk (P-031, BAIXA)
- **Dependências:** T-001, T-101
- **Ações (se T-001 mandar elevar):** subir `compileSdk`/`targetSdk` (36 para a Play), revisar mudanças de comportamento: serviços em primeiro plano (`dataSync` tem limite de tempo nas versões novas; a geração tem wake lock de 3 h), edge-to-edge, e suporte a páginas de 16 KB para bibliotecas nativas (relevante para o LAME de T-101). Se não: ENCERRADA.
- **Critérios:**
  - [ ] Decisão de T-001 aplicada ou encerrada com justificativa.

---

## FASE 7 — FECHO

### T-801 — Identidade visual: nome, ícone e boas-vindas (pedido do usuário, 15/11/2026)
- Nome exibido: **MixTape MP3** (`app_name`); `applicationId` inalterado. Ícone adaptativo (fita cassete, com versão monocromática) em `mipmap-anydpi-v26`; `WelcomeScreen` antes do Início (segue sozinha em ~2,5 s ou ao toque).
- Pendente do usuário: conferir se o nome já existe na Google Play antes de publicar.

### T-701 — Documentação (ALTA)
- Atualizar `docs/CHANGELOG.md`, `docs/PROGRESSO.md` (corrigir o "75/76"), `docs/ARQUITETURA.md` (`media/merger` não existe; concatenação está em `media/engine/CdConcatenator.kt`) e `docs/BUILD.md`.
- **Critério:** [ ] docs refletem o código após as correções.

### T-702 — Checklist de validação manual (MÉDIA)
- Produzir roteiro para o usuário: compilação; CBR 320 e duração do MP3; tags ID3/CHAP/CUE em players externos; notificação, Bluetooth e ligação durante reprodução; serviço em primeiro plano e wake lock; SAF/FileProvider; limite de permissões; destino FAT32; CD longo; modo avião.
- **Critério:** [ ] checklist entregue e executado pelo usuário.

### T-703 — APK final (ALTA)
- `./gradlew assembleRelease` com `keystore.properties` local; instalar e executar o checklist.
- **Critério:** [ ] APK assinado instalado e aprovado pelo usuário.

---

## Cobertura das pendências

| Pendência | Tarefa | Observação |
|---|---|---|
| P-001 | T-101 | IMPLEMENTADA (aguardando validação) |
| P-002 | T-102 | |
| P-003 | T-103 | |
| P-004 | T-501 | |
| P-005 | T-502 | |
| P-006 | T-303 | |
| P-007 | T-503 | |
| P-008 | T-504 | |
| P-009 | T-505 | |
| P-010 | T-402 | |
| P-011 | T-203 | |
| P-012 | T-401 | |
| P-013 | T-202 | |
| P-014 | T-301 | |
| P-015 | T-001, T-304 | ENCERRADA: não exigido |
| P-016 | T-201 | IMPLEMENTADA (aguardando validação) |
| P-017 | T-302 | |
| P-025 | T-601 | IMPLEMENTADA (aguardando validação) |
| P-030 | T-001, T-205 | exclusão exigida (decisão 15/11/2026) |
| P-031 | T-001, T-602 | IMPLEMENTADA (aguardando validação); Play exige API 36 desde 31/08/2026 |
| P-032 | T-001, T-303 | por CD exigiria schema |
| P-035 | — | OBS: ciclo de pacotes mantido |
| N-01 | T-204 | IMPLEMENTADA (aguardando validação) |
| T-205 | IMPLEMENTADA (aguardando validação) |
| P-018…P-024, P-026…P-029, P-033, P-034 | — | **não reconstruídos** (sem `data.py`) |

Fonte do requisito da Play: [Google Play Console Help — target API level](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-419).

### T-802 — Importação por link do YouTube (pedido do usuário, 15/11/2026)
- **Arquivos:** `media/connectors/{YouTubeConnector,YouTubeApi,Platform,ConnectorRegistry,RestrictedPlatformConnector,ImportErrors}.kt`
- **Status:** IMPLEMENTADA (aguardando validação)
- **Decisão:** Innertube direto, sem dependência nova e sem GPL; `YouTubeApi` isola o transporte.
- **Fora de escopo:** playlist, canal, transmissão ao vivo, resolução do parâmetro `n`.
- **Critério:** [ ] checklist de YouTube em `docs/CHECKLIST_VALIDACAO.md` executado pelo usuário.

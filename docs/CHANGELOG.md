# CHANGELOG
## [dev] 1.1 — Criar projeto Android
- Estrutura Gradle, módulo :app, manifest, tema, MainActivity inicial.

## [dev] 1.2 — Kotlin e Jetpack Compose
- Compose BOM 2024.06.00, Material3, activity-compose; MainActivity migrada para ComponentActivity + setContent.

## [dev] 1.3 — Navegação e identidade visual
- Tema Material3 claro/escuro (roxo/teal), Destination enum, AppNavHost (navigation-compose), HomeScreen.

## [dev] 1.4 — Estrutura de pacotes
- Pacotes core/common, feature/* e telas por feature ligadas ao NavHost; docs/ARQUITETURA.md.

## [dev] 1.5 — Permissões e armazenamento
- StorageAccess (SAF, URIs persistidos, pasta de saída), seletor de pasta na Home, POST_NOTIFICATIONS.
- Fase 01 concluída. Sem suíte de testes disponível; build Gradle não executado (sem SDK/rede).

## [dev] 2.1 — Room/SQLite
- KSP + Room 2.6.1, AppDatabase singleton, TrackEntity mínima, exportação de schema.

## [dev] 2.2 — Cadastro de músicas
- TrackEntity completa, TrackDao, TrackRepository, CDVirtualApp (Application) com acesso ao repositório.

## [dev] 2.3 — Importar arquivos locais
- Seletor SAF múltiplo, permissão persistida, LibraryViewModel, lista da biblioteca; ignora URIs já importados.

## [dev] 2.4 — Metadados, duração e formato
- AudioMetadataReader (MediaMetadataRetriever): título, artista, álbum, duração, formato; arquivos não-áudio são rejeitados e contados como erro.

## [dev] 2.5 — Capas
- Capa embutida extraída para filesDir/covers (nome por SHA-1), coluna coverPath, CoverImage com decodificação reduzida.

## [dev] 2.6 — Busca e exclusão
- Busca LIKE (título/artista/álbum) via flatMapLatest; exclusão remove registro e capa órfã, preservando o arquivo original.

## [dev] 2.7 — Duplicados
- Hash SHA-256 do conteúdo (coluna contentHash); duplicatas por conteúdo são ignoradas na importação e contabilizadas na mensagem.
- Fase 02 concluída. Sem suíte de testes disponível; build não executado (sem SDK/rede).

## [dev] 3.1 — Campo de URLs
- UrlInput (validação/normalização http/https), ImporterViewModel e ImporterScreen com campo de link.

## [dev] 3.2 — Identificar plataforma
- Platform/PlatformDetector por host (YouTube, Facebook, Instagram, 4shared; demais = link direto).

## [dev] 3.3 — Analisar link
- Permissão INTERNET; Http (redirecionamentos manuais), LinkAnalyzer (HEAD com fallback GET/Range): tipo, tamanho, nome do arquivo, é áudio ou não.
- Links http sem TLS são bloqueados pelo Android (cleartext); apenas HTTPS é suportado (ASSUMINDO).

## [dev] 3.4 — Formatos e qualidades
- MediaOption (formato, mime, tamanho, bitrate opcional) e seleção na UI. Link direto = 1 opção "Original" (bitrate não é presumido).

## [dev] 3.5 — Importação de links diretos
- DirectDownloader (streaming, .part, cancelável), TrackImporter unificado (biblioteca usa o mesmo fluxo), arquivos baixados em filesDir/imports.

## [dev] 3.6 — Conectores
- SourceConnector, DirectLinkConnector, ConnectorRegistry; ImporterViewModel resolve o conector pela plataforma.

## [dev] 3.7 — Fontes consideradas
- RestrictedPlatformConnector (YouTube/Facebook/Instagram: reconhece e orienta, sem extração); 4shared via link direto; docs/IMPORTADOR.md com a política.

## [dev] 3.8 — Progresso, cancelamento e erros
- Barra de progresso, botão Cancelar (análise e download, remove .part), mensagens de erro amigáveis (rede, HTTP, TLS, cleartext).
- Fase 03 concluída. Sem suíte de testes; build não executado (sem SDK/rede).

## [dev] 4.1 — Analisar volume
- PcmDecoder (MediaExtractor+MediaCodec → PCM float), LoudnessAnalyzer (pico e RMS), AnalysisRunner em segundo plano (somente leitura dos originais); colunas peakDb/rmsDb.

## [dev] 4.2 — LUFS
- Biquad (ponderação K), LoudnessAnalyzer com loudness integrado BS.1770 (gating -70/-10), true peak 4x estimado; colunas lufs/truePeakDb.

## [dev] 4.3 — Ganho automático
- NormalizationSettings (padrão -14 LUFS, -1 dBTP) com persistência, GainPlanner.autoGainDb, EqualizerViewModel/Screen com ganho por faixa. O ganho é aplicado na pré-visualização (4.8) e no motor de áudio (Fase 06); originais não são alterados.

## [dev] 4.4 — Anti-clipping
- GainPlanner.plan limita o ganho pelo true peak (máx. -1 dBTP); UI avisa quando o ganho é limitado.

## [dev] 4.5 — Volume individual
- Coluna userGainDb (-12..+12 dB, passo 0,5), slider por faixa, ganho total = automático + individual, sempre limitado pelo true peak.

## [dev] 4.6 — Equalizador básico
- Biquads RBJ (low shelf, peaking, high shelf), ThreeBandEqualizer, colunas bassDb/midDb/trebleDb (±12 dB), sliders por faixa; o limite de ganho considera o reforço máximo do EQ.

## [dev] 4.7 — Equalização automática do CD inteiro
- Análise espectral por banda (3 faixas) na mesma passagem da análise de loudness; AutoEqPlanner aproxima o equilíbrio tonal de cada faixa da média do conjunto; botões "EQ automática do CD" e "Zerar EQ". Schema Room v6.

## [dev] 4.8 — Pré-visualização
- PreviewPlayer (trecho de 15 s, A/B original/processado), botões Original/Processado/Parar por faixa, tratamento de erro.

## [dev] 4.9 — Preservar originais
- OriginalsGuard (leitura somente leitura, verificação de integridade), verificação na tela de equalização.

## [dev] Fase 04 concluída (4.1–4.9)
- Normalização, ganho, anti-clipping, EQ manual/automática, pré-visualização e preservação de originais.

## [dev] 5.1 — Criar novo CD
- Entidade/DAO de CD, repositório, ViewModel e tela do editor com criação e lista de CDs.

## [dev] 5.2 — Nome e capa do CD
- Renomear e escolher/remover capa do CD; capas listadas nos cartões.

## [dev] 5.3 — Selecionar músicas
- Tabela de junção cd_tracks, adição de músicas da biblioteca ao CD, lista de faixas do CD.

## [dev] 5.4 — Reordenar músicas
- Mover faixas para cima/baixo no CD, ordem persistida.

## [dev] 5.5 — Remover músicas do CD
- Remoção de faixas do CD sem afetar a biblioteca.

## [dev] 5.6 — Duração total
- Duração total do CD e por faixa, atualizadas a cada alteração.

## [dev] 5.7 — Tamanho estimado
- Estimativa do MP3 final (320 kbps CBR) no CD atual.

## [dev] 5.8 — Salvar projetos
- Salvar projeto, data de atualização e reabertura do último CD editado.

## [dev] Fase 05 concluída (5.1–5.8)
- Editor de CD completo: criar, nome/capa, selecionar, reordenar, remover, duração, tamanho estimado e projetos salvos.

## [dev] 6.1 — Decodificar
- EngineDecoder com progresso, cancelamento e tratamento de erros para o motor de áudio.

## [dev] 6.2 — Padronizar formato
- Reamostragem para 44,1 kHz e estéreo para todas as faixas do CD.

## [dev] 6.3 — Aplicar normalização e EQ
- Cadeia completa de processamento por faixa no motor de áudio.

## [dev] 6.4 — Concatenar
- Concatenação gapless das faixas processadas com registro das posições.

## [dev] 6.5 — Gerar MP3 único
- Pipeline completo CD -> MP3 único, dependência LAME (TAndroidLame/JitPack).

## [dev] 6.6 — 320 kbps CBR
- Especificação fixa e verificação do MP3 gerado por varredura de quadros.

## [dev] 6.7 — Segundo plano
- Geração do MP3 em serviço em primeiro plano com notificação de progresso e estado na tela.

## [dev] 6.8 — Cancelamento e recuperação
- Cancelamento seguro e recuperação de gerações interrompidas.

## [dev] 6.9 — Validar integridade
- Validação completa do MP3 antes da entrega.

## [dev] Fase 06 concluída (6.1–6.9)
- Motor de áudio: decodificação, padronização, norm/EQ, concatenação, MP3 320 kbps CBR, serviço em segundo plano, cancelamento/recuperação e validação.

## [dev] 7.1 — Tags ID3
- Escrita de tag ID3v2.3 (título e ano) no MP3 final.

## [dev] 7.2 — Álbum
- Tag TALB com o nome do CD.

## [dev] 7.3 — Artista e capa
- CdEntity.artist (schema v10), campo "Artista" no editor de CD; Id3Tag.picture (APIC) e quadros TPE1/TPE2; ExportService passa artista e capa ao CdMetadata.

## [dev] 7.4 — Duração
- Quadro TLEN (Id3Tag.numeric) com a duração total em ms, preenchido pelo Mp3Exporter.

## [dev] 7.5 — Posição/tempo das faixas
- Id3Tag.chapters (CHAP/CTOC) + Mp3Exporter.withTimings: um capítulo por faixa com início/fim reais.

## [dev] 7.6 — CUE opcional
- media/metadata/CueSheet (build/time/writeNextTo) e CueSettings; opção no editor de CD; ExportService grava <nome>.cue ao lado do MP3 quando ligada.

## [dev] Fase 07 concluída (7.1–7.6)
- ID3v2.3 completo (título, álbum, artista, ano, duração, capa, capítulos por faixa) e CUE opcional.

## [dev] 8.1 — Reproduzir
- media/playback/Mp3Player (MediaPlayer, PlayerState via StateFlow) e PlayerScreen com a lista de MP3 gerados e botão Reproduzir.

## [dev] 8.2 — Pausar/continuar
- Mp3Player.pause/resume/togglePlayPause e botão Pausar/Continuar na tela Player.

## [dev] 8.3 — Avançar/retroceder
- Mp3Player.seekTo/seekBy/positionMs e botões ⏪ 15 s / 15 s ⏩.

## [dev] 8.4 — Nome atual
- media/metadata/Id3Reader (capítulos CHAP), PlayerState.chapters/chapterAt e exibição do nome da faixa atual.

## [dev] 8.5 — Capa
- Id3Reader.readCover/parseCover (APIC) e imagem da capa na tela Player.

## [dev] 8.6 — Barra de progresso
- Slider de posição com busca ao soltar e texto mm:ss / duração na tela Player.

## [dev] 8.7 — Segundo plano
- media/playback/PlaybackService (foreground mediaPlayback), Mp3Player.play(context, file) com wake lock, manifest atualizado.

## [dev] 8.8 — Notificação
- PlaybackService: notificação de mídia com controles (±15 s, pausar/continuar, parar) e título da faixa atual.

## [dev] 8.9 — Bluetooth
- PlaybackService: MediaSession (controles AVRCP e metadados), foco de áudio e pausa ao desconectar fones; Mp3Player.setVolume.

## [dev] 8.10 — Navegação por tempos
- Mp3Player.nextChapter/previousChapter, lista de faixas com tempos na tela Player e próximo/anterior via MediaSession.

## [dev] Fase 08 concluída (8.1–8.10)
- Player offline completo: reproduzir, pausar, buscar, nome, capa, progresso, segundo plano, notificação, Bluetooth e navegação por faixas.

## [dev] 9.1 — Pasta de destino
- core/storage/OutputFolder (estado NotSet/Ready/Unavailable) e ExportScreen com escolher/trocar/esquecer pasta.

## [dev] 9.2 — Exportar MP3
- core/storage/FolderExporter (cópia SAF com progresso, nome único, limpeza em falha) e lista de MP3 com botão Exportar MP3 na ExportScreen.

## [dev] 9.3 — Exportar CUE
- CueSheet.fromMp3/encode, Id3Reader.readText, FolderExporter.writeBytes e botão CUE na ExportScreen.

## [dev] 9.4 — Compartilhar
- core/storage/ShareFiles, FileProvider no manifest (res/xml/file_paths.xml) e botão Compartilhar na ExportScreen.

## [dev] 9.5 — Gerenciar CDs
- feature/export/GeneratedCds (listagem com metadados, total, renomear) e ExportScreen reorganizada em cartões por CD, com rolagem.

## [dev] 9.6 — Excluir exportados
- GeneratedCds.delete, FolderExporter.delete e diálogo de confirmação (com opção de apagar as cópias exportadas na sessão).

## [dev] Fase 09 concluída (9.1–9.6)
- Exportação: pasta SAF, MP3, CUE, compartilhamento, gerenciador e exclusão.

## [dev] 10.1 — Correções
- ExportService: startForeground garantido em todos os caminhos de ACTION_START; ExportState.Running no pedido, Failed se o serviço for recusado.
- AndroidManifest: allowBackup=false. TrackImporter: CancellationException propagada.

## [dev] 10.2 — Memória
- Id3Reader.readFrames (seek por quadro, só corpos pedidos) e Id3Reader.readInfo (leitura única); GeneratedCds/CueSheet usam readInfo.
- ExportScreen e PlayerScreen: leitura de tags e decodificação de capa em Dispatchers.IO.

## [dev] 10.3 — Arquivos grandes
- ExportService.checkFreeSpace (falha cedo sem espaço); FolderExporter: dica de FAT32 para >4 GiB; CdEditorScreen: aviso acima de 4 GB.

## [dev] 10.4 — Qualidade
- media/engine/Pcm16Quantizer (dither TPDF) usado no Mp3Exporter; ThreeBandEqualizer sem alocações por amostra.

## [dev] 10.5 — Interrupções
- Manifest: configChanges na MainActivity. PreviewPlayer: foco de áudio. ImportRecovery + checagem de download incompleto + mensagens de rede.

## [dev] 10.6 — Players externos
- ShareFiles.openWith + botão "Abrir em outro player" na ExportScreen; CueSheet com Locale.ROOT.

## [dev] 10.7 — Sem internet
- core/common/NetworkStatus + ACCESS_NETWORK_STATE; ImporterViewModel/ImporterScreen avisam offline.

## [dev] 10.8 — APK final (preparação)
- Assinatura de release opcional, ícone, versão 1.0.0 e docs/BUILD.md. APK não gerado (sem SDK/rede).

## [correções] T-101 — IMPLEMENTADA (aguardando validação)
- T-101/P-001: roteiro de verificação do encoder LAME em docs/BUILD.md e nota em LameMp3Encoder; API não alterada por não estar confirmada (ASSUMINDO mantido).

## [correções] T-102 — IMPLEMENTADA (aguardando validação)
- T-102/P-002: gradle-wrapper.properties (Gradle 8.7) e passo a passo para gerar gradlew/jar em docs/BUILD.md; .jar não pode ser criado offline.

## [correções] T-103 — IMPLEMENTADA (aguardando validação)
- T-103/P-003: fallbackToDestructiveMigrationFrom(1..9) no lugar do destrutivo geral; regra de migração documentada; schema inalterado.

## [correções] T-204 — IMPLEMENTADA (aguardando validação)
- T-204/N-01: TrackImporter.import executa em Dispatchers.IO (SHA-256 e metadados fora da thread principal).

## [correções] T-201 — IMPLEMENTADA (aguardando validação)
- T-201/P-016: permissão URI persistida só após leitura válida; liberada ao remover faixa e em falha; aviso perto do limite (128/512, ASSUMINDO); SecurityException tratada.

## [correções] T-203 — IMPLEMENTADA (aguardando validação)
- T-203/P-011: HomeScreen usa OutputFolder (mesmo estado/validação/liberação da tela Exportar).

## [correções] T-202 — IMPLEMENTADA (aguardando validação)
- T-202/P-013: remover faixa apaga o download em filesDir/imports; limpeza de órfãos na abertura do app; nada fora de imports/ é tocado.

## [correções] T-301 — IMPLEMENTADA (aguardando validação)
- T-301/P-014: AnalysisRunner expõe falhas por faixa (memória) e permite tentar de novo; LibraryScreen mostra o motivo no lugar de 'Analisando…'.

## [correções] T-302 — IMPLEMENTADA (aguardando validação)
- T-302/P-017: fases do true peak em constante; sem alocação por amostra; cálculo idêntico.

## [correções] T-303 — IMPLEMENTADA (aguardando validação)
- T-303/P-006: EQ automática e Zerar EQ aplicam ao CD escolhido (seletor na tela Equalizador); biblioteca inteira só quando sem CD; schema inalterado.

## [correções] T-401 — IMPLEMENTADA (aguardando validação)
- T-401/P-012: ExportNaming gera nome único por CD (posse em SharedPreferences); regerar o mesmo CD substitui só o seu arquivo; delete/rename mantêm a posse.

## [correções] T-402 — IMPLEMENTADA (aguardando validação)
- T-402/P-010: ExportCopies persiste nome gravado e URIs de cópias (MP3/CUE) em SharedPreferences; limpo ao excluir, movido ao renomear.

## [correções] T-501 — IMPLEMENTADA (aguardando validação)
- T-501/P-004: Editor de CD em um único LazyColumn rolável (detalhes, faixas, botões e lista de CDs).

## [correções] T-502 — IMPLEMENTADA (aguardando validação)
- T-502/P-005: Player em um único LazyColumn (controles, capítulos e lista de MP3 rolam juntos).

## [correções] T-503 — IMPLEMENTADA (aguardando validação)
- T-503/P-007: helper rememberNotificationGate pede POST_NOTIFICATIONS antes de gerar/tocar (Editor, Player, Exportar); a ação roda mesmo se negada.

## [correções] T-504 — IMPLEMENTADA (aguardando validação)
- T-504/P-008: Mp3Player usa prepareAsync + OnPrepared; capítulos lidos em IO e publicados depois; estado 'Carregando' na tela.

## [correções] T-505 — IMPLEMENTADA (aguardando validação)
- T-505/P-009: foco de áudio centralizado em Mp3Player (pede ao tocar/retomar, abandona ao liberar/terminar); PlaybackService deixou de ter foco próprio.

## [correções] T-601 — IMPLEMENTADA (aguardando validação)
- T-601/P-025: removidos sem uso `runCatchingResult`, `StorageAccess.hasPersistedAccess`, `Id3Reader.readText/parseCover/parseChapters/readTag`, `TrackRepository.update/findById`.
- `formatBytes` (ExportScreen) substituída por `formatSize`; novo `core/common/CoverDecoder` unifica a decodificação reduzida de capa (CoverImage, PlayerScreen, PlaybackService, CoverPrepare); caminho `filesDir/exports` centralizado em `ExportRecovery.exportsDir` (GeneratedCds, PlayerScreen).
- Sem mudança de comportamento intencional (única diferença: `formatSize` passa a exibir GB em tamanhos ≥ 1 GB). P-035 mantida como OBS. Não compilado (sem SDK/rede).

## [correções] T-001 / T-304 / T-205 (criação) — decisões registradas
- T-001 CONCLUÍDA (usuário, 15/11/2026): P-015 não exigido; P-030 exigido; P-031 Google Play (API 36); P-032 por faixa.
- T-304 ENCERRADA: requisito 3.4 não exige bitrate em links diretos; `LinkAnalyzer` mantido.
- Criada T-205 (exclusão de projeto de CD) por P-030; T-602 liberada. Denominador de progresso corrigido para 25 tarefas.

## [correções] T-205 — IMPLEMENTADA (aguardando validação)
- T-205/P-030: `CdDao.delete`, `CdRepository.delete` (apaga capa órfã), `CdEditorViewModel.deleteCd` e botão "Excluir CD" com confirmação. Sem mudança de schema (cascade já existente). Músicas, originais e MP3 gerados não são tocados.

## [correções] T-602 — IMPLEMENTADA (aguardando validação)
- T-602/P-031: `compileSdk`/`targetSdk` 36; `android.suppressUnsupportedCompileSdk=36`; edge-to-edge com `safeDrawingPadding` na `MainActivity`; `ExportService.onTimeout` (limite do serviço dataSync na API 35+). Riscos de AGP/Gradle e de páginas de 16 KB do LAME documentados em `docs/BUILD.md`. Não compilado.

## [correções] T-701 / T-702 — IMPLEMENTADAS
- T-701: PROGRESSO.md (75/76 corrigido e estado das correções), ARQUITETURA.md (`media/merger` não existe; concatenação em `media/engine/CdConcatenator.kt`) e BUILD.md atualizados.
- T-702: checklist de validação manual em docs/CHECKLIST_VALIDACAO.md (execução pelo usuário).

## [extra] T-801 — IMPLEMENTADA (aguardando validação)
- Pedido do usuário (fora do Roadmap original): app renomeado para "MixTape MP3"; ícone adaptativo de fita cassete (com camada monocromática); tela de boas-vindas (`feature/welcome/WelcomeScreen`) como destino inicial, seguindo para o Início sem voltar pelo botão Voltar. Textos "CD Virtual" visíveis ao usuário trocados. `applicationId` mantido. Não compilado.

## 1.1.0 — importação por link do YouTube
- `YouTubeConnector` e `YouTubeApi` (novos); `PlatformDetector.videoIdOf`; YouTube fora de `RestrictedPlatformConnector`; `YouTubeFailure`/`YouTubeUnavailableException` em `ImportErrors`.
- Seleção entre as faixas de áudio disponíveis, ordenadas por bitrate; download via `DirectDownloader` (progresso, cancelamento, `.part`, verificação de download incompleto).
- IMPLEMENTADA (aguardando validação): nada compilado nem executado em aparelho.

## [dev] Integração das melhorias de uso sobre a branch feat/youtube-connector (2026-11-16)
Base oficial: branch `feat/youtube-connector` (navegador integrado, fila de até 5 downloads, tela Downloads, `DownloadService`, conector do YouTube, personalização de áudio por playlist, biblioteca com player). Foram trazidas do snapshot anterior apenas as melhorias abaixo; nada da base foi removido.
- Configurações (`feature/settings/SettingsScreen`, acessível pelo Início): pasta de destino, "Salvar automaticamente ao gerar" (`core/common/AppSettings`), arquivo CUE, alvo de volume e sobre.
- Playlist gerada é copiada sozinha para a pasta de destino (substitui a cópia anterior ao regerar; CUE junto, se ligado); `ExportStatus.Done` ganhou `savedTo`, `saveNote`, `deletedOriginals`, `failedDeletes`.
- Diálogo "Gerar playlist" com a opção de apagar os originais (`core/storage/OriginalsCleaner`): só roda com o MP3 pronto e, havendo cópia automática, copiado sem erro. A personalização de áudio por playlist (`CdAudioSettings`) continua valendo na geração.
- Biblioteca: abas Músicas (agrupadas por gênero, recolhíveis, gênero editável, busca também por gênero) e Playlists (músicas lidas dos capítulos do MP3; toque em uma música abre a playlist naquele ponto, `Mp3Player.play(context, file, startMs)`). Mantidos: reproduzir tudo, aleatório, mini player com busca/volume, qualidade padrão dos downloads, ▶ por música.
- Room v11 (coluna `genre`, `Migration` 10→11, preenchimento das músicas antigas ao abrir, `GenreBackfill`). Exige gerar `app/schemas/.../11.json` no primeiro build.
- Moldura padrão `core/ui/ScreenScaffold` com "← Voltar" em Biblioteca, Importar, Downloads, Playlists, Volume e equalização, Player, Exportar e Configurações (Navegador mantém o botão Início); `ButtonFlow` (botões quebram linha); correção de itens de `LazyColumn` com vários elementos sem `Column` (sobreposição no editor de playlists e no equalizador).
- Exportar: botão "Abrir em outro player" junto dos demais. Player: controle do volume do aparelho (além do volume do player) e `volumeControlStream` = música na `MainActivity`.
- Home: acesso a Configurações; contador de ferramentas deixou de ser fixo.
- Não compilado nem executado em aparelho (sem SDK/rede no ambiente de integração).



## [dev] 2026-11-26 — Roadmap Central de Vídeos e Cache Offline
- Confirmado baseline remoto da `main`: `1a80446cf53485dabd56057c0fe385e2e9a5ae24`.
- Auditoria estática confirmou domínios separados: biblioteca musical (`tracks`), histórico de vídeos (`youtube_history`) e canais (`youtube_channels`), com serviços de reprodução independentes.
- Criado `docs/ROADMAP_CACHE_OFFLINE.md` para controlar a evolução sem substituir o roadmap original de 76 submódulos.
- Registrada a regra de retenção: cache só é removido ao concluir naturalmente a visualização ou por ação manual.
- Registradas as qualidades planejadas: 240p padrão, 360p e 480p; limite configurável e sem remoção automática para abrir vaga.
- A validação funcional foi transferida para a fase final. Nenhum teste ou build foi executado nesta etapa.
- A integração de cache persistente de vídeos do YouTube fica condicionada a uma via oficialmente autorizada; não será implementado contorno de restrições da fonte.

- Corrigido o vazamento de histórico: vídeos abertos como prévia no navegador da Central de Áudio não são registrados nem têm progresso gravado na biblioteca/histórico da Central de Vídeos. Reproduções iniciadas pela Central de Vídeos mantêm o registro normal.

- Criado `MediaStorageLayout` para separar fisicamente importações de áudio (`filesDir/imports`, caminho legado preservado) e cache de vídeo (`filesDir/video_cache`). `ImporterViewModel` e `ImportRecovery` agora usam explicitamente o diretório de áudio.

- Criados `VideoCacheEntity`, `VideoCacheDao`, estados/qualidades e migração Room v13→v14. O domínio de cache é isolado das tabelas de áudio e do histórico/canais do YouTube.
- Nenhum teste ou build foi executado; geração/registro do schema Room 14 foi transferida para a validação final.

- Criado `VideoCacheRepository`, com validação de metadados/qualidade/estado e acesso via `CDVirtualApp`. O repositório não manipula arquivos; a exclusão do registro exige que o gerenciador já tenha removido o arquivo físico.
- Corrigidos os delimitadores da SQL da migração v13→v14 para string Kotlin multilinha válida. A verificação funcional e o build continuam reservados à fase final.

- Adicionado `VideoCacheStateMachine` para restringir transições de estado e `VideoCacheFileStore` para manter arquivos em diretório isolado, publicar parciais atomicamente e validar integridade/caminho. `READY` agora exige `markReadyAfterPromotion` após validação do arquivo. Nenhuma rotina apaga parciais automaticamente.

- Implementada recuperação inicial do Cache Offline: reconcilia estados do banco com arquivos, preserva parciais retomáveis, pausa reproduções interrompidas com arquivo válido e finaliza exclusões concluídas pendentes. Arquivos órfãos são identificados sem limpeza destrutiva.

- Criado `VideoCacheManager` e `VideoCacheSettingsStore`: limite configurável (padrão 3), reservas sem sobrescrever itens, capacidade de armazenamento, remoção manual e conclusão natural com exclusão do arquivo. O gerenciador não resolve URLs nem baixa streams; essa integração fica condicionada a fonte autorizada.

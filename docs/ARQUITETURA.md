# ARQUITETURA
Módulo Gradle único (:app) com pacotes em com.cdvirtual.app (evita módulos vazios, conforme regras técnicas).
- navigation/ — Destination, AppNavHost
- ui/theme/ — identidade visual
- core/common — AppResult e utilitários; core/storage — SAF (1.5); core/database — Room (Fase 02, criado quando houver conteúdo)
- feature/{home,library,importer,cdeditor,equalizer,player,export} — telas por funcionalidade
- media/{connectors,decoder,normalization,equalization,encoder,engine,metadata,playback,service} — criados nas Fases 03, 04, 06 e 07 quando houver código real

## Armazenamento e permissões
- SAF: OpenDocument/OpenDocumentTree com permissão persistida (core/storage/StorageAccess). Sem READ/WRITE_EXTERNAL_STORAGE.
- POST_NOTIFICATIONS declarada para as Fases 06/08. Permissões de serviço em primeiro plano serão adicionadas junto aos serviços.

## [dev] 4.7 — EQ automática do CD
- LoudnessAnalyzer calcula, na mesma passagem, energia por banda (graves <250 Hz, médios 250 Hz–4 kHz, agudos >4 kHz) via Biquad.lowPass/highPass; colunas bandBassDb/bandMidDb/bandTrebleDb (schema v6).
- AutoEqPlanner: equilíbrio (graves/agudos relativos aos médios) de cada faixa é aproximado da média do conjunto (força 0,6, máx. ±6 dB, passo 0,5 dB); médios = 0. EqualizerScreen: botões "EQ automática do CD" e "Zerar EQ". Ajustes continuam editáveis por faixa; originais intactos.

## [dev] 4.8 — Pré-visualização
- media/preview/PreviewPlayer: decodifica 15 s do meio da faixa (PcmDecoder), aplica ganho planejado + ThreeBandEqualizer e toca via AudioTrack float; A/B original x processado na EqualizerScreen. Somente leitura.

## [dev] 4.9 — Preservar originais
- Auditoria: nenhum fluxo grava/apaga a origem (permissão SAF somente leitura; remoção da biblioteca apaga só registro e capa). core/storage/OriginalsGuard: leitura sempre em modo "r" e verificação de integridade por SHA-256 (INTACT/MODIFIED/MISSING/UNVERIFIED); botão "Verificar originais" na EqualizerScreen.

## [dev] 5.1 — Novo CD
- core/database: CdEntity/CdDao (tabela cds, schema v7); feature/cdeditor: CdRepository (nome padrão "Novo CD N"), CdEditorViewModel, CdEditorScreen (botão Novo CD, lista, seleção do CD atual).

## [dev] 5.2 — Nome e capa
- CdEntity.coverPath; CdRepository.rename (trim, máx. 80, recusa vazio) e setCover (apaga capa antiga só se sem uso); CoverPrepare valida/reduz a imagem; CdEditorScreen com detalhes do CD atual.

## [dev] 5.3 — Selecionar músicas
- cd_tracks (CdTrackEntity: cdId, trackId, position; FKs CASCADE), CdDao.observeTracks/insertTracks, CdRepository.addTracks (anexa ao fim, sem repetir); diálogo de seleção multipla na CdEditorScreen.

## [dev] 5.4 — Reordenar
- CdRepository.moveTrack reescreve as posições 0..n-1 de cd_tracks; botões ↑/↓ por faixa na CdEditorScreen.

## [dev] 5.5 — Remover do CD
- CdRepository.removeTrack apaga só a linha de cd_tracks e renumera posições; biblioteca e originais intactos. Botão ✕ por faixa.

## [dev] 5.6 — Duração total
- CdStats.totalDurationMs (soma das durações; desconhecidas = 0); exibida no CD atual junto da duração por faixa (formatDuration).

## [dev] 5.7 — Tamanho estimado
- CdStats.estimatedSizeBytes = ms x kbps / 8 com MP3_KBPS = 320; exibido ao lado da duração total.

## [dev] 5.8 — Salvar projetos
- Persistência contínua no Room (cds + cd_tracks), CdRepository.touch, botão Salvar projeto, data de atualização na lista e restauração do último CD aberto (prefs "cdeditor").

## [dev] 6.1 — Decodificar
- media/engine/EngineDecoder: PCM float no formato nativo, progresso por duração, cancelamento, erros legíveis (DecodeException), originais só leitura.

## [dev] 6.2 — Padronizar
- media/engine/AudioStandardizer: sinc janelada polifásica (2048 fases, 16 taps/lado, passa-baixas ao reduzir taxa), downmix para estéreo; StandardPcm.decode = EngineDecoder + padronização em fluxo.

## [dev] 6.3 — Aplicar norm/EQ
- media/engine/ProcessedPcm: StandardPcm + ThreeBandEqualizer + ganho do GainPlanner (limitado por true peak) + clip de segurança ±1.

## [dev] 6.4 — Concatenar
- media/engine/CdConcatenator: fluxo PCM contínuo na ordem do CD, progresso ponderado pela duração, ConcatResult com TrackSpan (início/duração em quadros e ms) por faixa.

## [dev] 6.5 — MP3 único
- media/encoder: Mp3Encoder (interface) + LameMp3Encoder (única classe que toca a biblioteca). media/engine/Mp3Exporter: concat -> PCM16 -> encoder -> "<nome>.part" com fsync e rename ao final; parcial removido em falha/cancelamento.

## [dev] 6.6 — 320 kbps CBR
- Mp3Spec (320 kbps, 44,1 kHz, estéreo) como fonte única; Mp3FrameScanner lê cabeçalhos MPEG-1 L3 (ignora ID3v2/ID3v1) e Mp3Exporter só finaliza o arquivo se todos os quadros forem 320 kbps CBR.

## [dev] 6.7 — Segundo plano
- media/service: ExportService (foreground dataSync, notificação com progresso, wake lock), ExportState/ExportStatus (StateFlow). Botão "Gerar MP3" no CD atual (pede POST_NOTIFICATIONS no Android 13+). Manifest: FOREGROUND_SERVICE(_DATA_SYNC), WAKE_LOCK.

## [dev] 6.8 — Cancelamento e recuperação
- Cancelar pela notificação ou pela tela (ACTION_CANCEL -> job.cancel; Mp3Exporter remove o ".part"); estado Cancelled. ExportRecovery: marcador persistente + limpeza no início do app, estado Failed "interrompida".

## [dev] 6.9 — Validar integridade
- media/engine/Mp3Validator: existência, CBR 320/44,1 kHz sem bytes soltos, duração versus esperado e decodificação de início/fim; Mp3Exporter só finaliza o arquivo se o relatório for ok.

## [dev] 7.1 — ID3
- media/metadata: Id3Tag (ID3v2.3, quadros de texto UTF-16), Id3Writer.build/prepend, CdMetadata. Mp3Exporter grava a tag, revalida o arquivo e só então renomeia.

## [dev] 7.2 — Álbum
- CdMetadata.album (padrão = nome do CD) gravado em TALB.

## [dev] 10.2 — Memória
- Regra: nada proporcional à duração do áudio fica em memória; só blocos (OUT_FRAMES 4096) e a tag ID3 (≤32 MB, capa ≤8 MB). Id3Reader lê quadros por seek e descarta os não pedidos; telas leem tags fora da thread principal.

## [dev] 10.3 — Arquivos grandes
- Pico de disco na geração = ".part" + ".tagged" (+ MP3 antigo ao regerar); verificado antes de começar. Limite de 4 GiB só afeta destinos FAT32 (cópia SAF); o armazenamento interno não tem esse limite.

## [dev] 10.4 — Qualidade
- Cadeia final: decodificação float → 44,1 kHz estéreo → EQ → ganho (limite de true peak) → clip ±1 → dither TPDF + PCM16 → LAME 320 CBR.

## [dev] 10.5 — Interrupções
- Recuperação ao abrir o app: ExportRecovery (geração) e ImportRecovery (downloads). Foco de áudio: PlaybackService (player) e PreviewPlayer (pré-visualização).

## [dev] 10.6 — Players externos
- Saída pensada para outros players: MP3 CBR 320 + ID3v2.3 + CUE ASCII/UTF-8 com BOM; acesso a arquivos internos só via FileProvider (filesDir/exports).

## [dev] 10.7 — Sem internet
- Rede só em media/connectors (importador por link). Todo o resto é local; a checagem de conectividade fica em core/common/NetworkStatus.

## Atualizações pós-correções (T-701)
- `media/merger` não existe: a concatenação está em `media/engine/CdConcatenator.kt` (junto de `AudioStandardizer`, `Mp3Exporter`, `Mp3Validator`).
- Geração em `media/service` (`ExportService`, `ExportNaming`, `ExportRecovery`); reprodução em `media/playback` (`Mp3Player` com foco de áudio centralizado, `PlaybackService`).
- `core/common/CoverDecoder` é o ponto único de decodificação reduzida de capa; `ExportRecovery.exportsDir` é o único caminho de `filesDir/exports`.
- `core/storage/ExportCopies` persiste as cópias exportadas; `OutputFolder` é a única lógica de pasta de destino (Home e Exportar).
- Room: v10 é a linha de base da 1.0; toda mudança de schema pós-1.0 exige `Migration` e incremento de versão (nunca `fallbackToDestructiveMigration()` sem versões).
- `feature/welcome/WelcomeScreen`: destino inicial; `popUpTo` remove a tela da pilha ao entrar no Início.

## [dev] 11.1 — Importação por link do YouTube
- `media/connectors/YouTubeConnector` + `YouTubeApi`: o conector só extrai o ID (`PlatformDetector.videoIdOf`) e delega; `YouTubeApi` é o único ponto que fala com o YouTube (Innertube, cliente ANDROID, JSON via `org.json`, sem dependência nova). O download reusa `DirectDownloader`.
- YouTube saiu de `RestrictedPlatformConnector`; Facebook e Instagram continuam lá. A "regra técnica 8" (só meios permitidos) vale agora apenas para essas duas: baixar do YouTube contraria os ToS, e a documentação assume isso.
- Falhas de vídeo (privado, região, ao vivo, idade, sem áudio, mudança do player) viram `YouTubeUnavailableException` + `YouTubeFailure` em `ImportErrors.kt`.
- Risco conhecido: o parâmetro `n` de throttling não é resolvido (exigiria JS), então o download pode vir limitado em velocidade. Trocar de transporte = reescrever só `YouTubeApi.kt`.

## [dev] Integração: Configurações, gêneros e cópia automática
- `core/ui/ScreenScaffold` (barra com Voltar + título) e `ButtonFlow` são a moldura comum das telas internas; `AppNavHost` passa `onBack` (pop só se houver tela anterior).
- `feature/settings/SettingsScreen` usa `OutputFolder`, `AppSettings`, `CueSettings` e `NormalizationSettingsStore`; não duplica lógica de pasta.
- `ExportService.run` depois de gerar: cópia para a pasta (`FolderExporter` + `ExportCopies`) se `AppSettings.autoSaveToFolder`, depois `OriginalsCleaner` se pedido na geração. Falha na cópia nunca apaga originais.
- `feature/library/Genres` normaliza a tag (ID3v1 numérico e "(n)texto"); coluna `tracks.genre` (null = ainda não lido, "" = sem gênero); Room v11 com `MIGRATION_10_11`.



## Central de Vídeos e Central de Áudio (auditoria 2026-11-26)
- Home encaminha para destinos independentes: `Destination.YouTube` abre `feature/youtube/YouTubeScreen`; `Destination.Browser` abre `feature/browser/BrowserScreen` (fluxo de pesquisa/importação de áudio).
- A biblioteca musical usa exclusivamente `TrackEntity`, `TrackDao` e `TrackRepository`. O histórico da Central de Vídeos usa `YouTubeHistoryEntity/YouTubeHistoryDao`; canais salvos usam `YouTubeChannelEntity/YouTubeChannelDao`.
- Reprodução MP3 e YouTube possuem serviços separados: `Mp3Player/PlaybackService` e `YouTubePlaybackService`.
- O vídeo usado como origem para extração de áudio não é um item de vídeo da biblioteca musical; o item registrado na biblioteca é o arquivo de áudio importado.
- Não foi encontrada consulta cruzada entre as tabelas de faixas e as tabelas YouTube na inspeção estática. A validação funcional permanece na fase final.
- O Cache Offline deverá usar entidades, DAO, repositório e diretório próprios. A preparação offline só poderá integrar fontes que autorizem armazenamento local.

- A prévia nativa de vídeo iniciada pela Central de Áudio usa `YouTubePlaybackService.play(..., recordHistory = false)`. A reprodução iniciada pela Central de Vídeos mantém `recordHistory = true`; assim a prévia usada como origem de extração não contamina o histórico de visualização.

- `core/storage/MediaStorageLayout` define `filesDir/imports` como diretório legado exclusivo de importações de áudio e `filesDir/video_cache` como diretório exclusivo reservado ao cache de vídeo permitido. O caminho de imports não muda para preservar URIs persistidos.
- `ImportRecovery` e `ImporterViewModel` usam `MediaStorageLayout.audioImportsDir`; a recuperação de áudio não percorre o diretório de vídeos.

- Room v14 acrescenta `VideoCacheEntity` e `VideoCacheDao` em tabela própria `video_cache`; migração explícita v13→v14 cria tabela e índice status/data. Não há FK para `tracks`, `youtube_history` ou `youtube_channels`.
- Qualidades persistidas como altura 240/360/480; 240p é o padrão. Estados são strings controladas por `VideoCacheStatus`.
- O DAO persiste metadados e posição; não apaga arquivos físicos. A remoção de arquivos será responsabilidade do gerenciador de cache.

- `feature/youtube/VideoCacheRepository` encapsula o DAO do cache e valida identificadores, qualidade (240/360/480), estado e valores não negativos. A remoção de registro só é exposta com o contrato `deleteRecordAfterFileRemoval`, para impedir que a camada de persistência apague o metadado antes do arquivo físico.
- `CDVirtualApp.videoCacheRepository` fornece a instância compartilhada do repositório à Central de Vídeos.

- `core/storage/VideoCacheFileStore` mantém os arquivos em `filesDir/video_cache`, aceita apenas IDs/formatos controlados, publica `.part` por rename no mesmo diretório e valida caminho canônico/tamanho antes da disponibilidade. A listagem de parciais não apaga nada automaticamente.
- `VideoCacheStateMachine` restringe transições; `VideoCacheRepository.markReadyAfterPromotion` é o único caminho para definir `READY` e exige arquivo completo, não vazio e dentro do diretório do cache.

- `feature/youtube/VideoCacheRecovery` reconcilia Room e arquivos na inicialização. Downloads em andamento viram INTERRUPTED, reprodução com arquivo íntegro vira PAUSED, arquivos ausentes são marcados como interrompidos e estados COMPLETED/REMOVED são finalizados quando o arquivo gerenciado pode ser removido. Parciais e arquivos publicados sem registro são apenas reportados, nunca apagados automaticamente.

- `feature/youtube/VideoCacheManager` coordena reserva de vaga, limite configurável (padrão 3, presets 1/2/3/5/10 e personalizado até 100), transições de download/reprodução, publicação, conclusão natural e remoção manual. Alterar o limite não remove itens existentes.
- `VideoCacheSettingsStore` persiste o limite em SharedPreferences. O manager expõe capacidade (itens, limite, bytes usados e espaço disponível). A conclusão natural remove o arquivo e registro; pausa/interrupção preservam o cache.

## Cache temporário da Central de Vídeos (2026-11-26)
- `feature/youtube/VideoCacheSourceRegistry` define admissão explícita de fontes. O registro inicia vazio; um extrator de reprodução online não se torna automaticamente um provedor de download/offline.
- `feature/youtube/VideoCacheTransfer` é um transporte HTTP próprio da Central de Vídeos, baseado no OkHttp já presente no app. Exige HTTPS, MIME de vídeo e extensão MP4/WebM compatível; envia Range quando existe parcial, informa progresso e preserva `.part` em falhas/cancelamentos.
- `VideoCacheDownloadCoordinator` coordena reserva, verificação de espaço, transferência e publicação. `VideoCacheFileStore.getOrCreatePartialFile` permite retomar parciais sem tocar nos arquivos de áudio.
- `VideoCacheManager.publishDownloadedFile` conclui a publicação e o estado READY sem janela de cancelamento entre rename e persistência Room.
- A seção Cache fica dentro de `feature/youtube/YouTubeScreen` e lista somente itens do domínio de vídeo. Permite assistir/retomar e apagar manualmente; não consulta a biblioteca MP3.
- `YouTubePlaybackService` aceita itens locais pelo mesmo ExoPlayer/MediaSession. O identificador de cache é transportado em metadados próprios para impedir escrita no histórico YouTube; posição é persistida no `VideoCacheRepository`.
- A conclusão natural do player remove arquivo e registro. Seek manual até o fim não conclui a visualização. Pausa, troca de tela, fechamento do app, reinicialização e perda de conexão preservam o arquivo.
- O provedor YouTube permanece ausente até existir um mecanismo oficialmente autorizado para download/armazenamento local. A reprodução online existente não foi alterada.
- Regra inviolável de escopo: nenhum arquivo da Central de Áudio foi alterado nesta etapa.
- O serviço `YouTubePlaybackService` também é utilizado pela prévia de vídeo da Central de Áudio, por isso a integração offline é isolada por `EXTRA_CACHE_ID`: somente itens locais do cache entram no novo caminho; a prévia somente áudio e seu histórico desativado continuam no caminho anterior. Nenhum arquivo da Central de Áudio foi alterado.
- `VideoCacheFileStore` também usa `MediaExtractor` para exigir ao menos uma faixa `video/*` antes da promoção e ao resolver arquivos prontos; extensão `.mp4`/`.webm` isolada não basta para marcar READY.
- Operações de arquivo, exclusão, promoção e inspeção por `MediaExtractor` são executadas em `Dispatchers.IO`; a interface não deve analisar contêineres nem manipular arquivos grandes na thread principal.

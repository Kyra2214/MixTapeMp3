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

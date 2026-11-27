# CHECKPOINT: CENTRAL DE VÍDEOS / CACHE TEMPORÁRIO (2026-11-27)

- Branch: `feature/video-temporary-cache`; PR #11 permanece draft, aberto e sem merge.
- Serviço de primeiro plano exclusivo da Central de Vídeos: notificação/progresso/cancelamento, limite de três transferências no coordenador, encerramento protegido contra solicitações concorrentes e tratamento do timeout Android para serviço `dataSync`.
- Serviço protegido contra Intent inválida/nula: não encerra uma instância que ainda possui outras transferências ativas; encerra apenas quando não há trabalho em andamento.
- Transporte HTTP Range endurecido: resposta 206 precisa declarar tamanho total numérico e cobrir até o último byte do recurso; respostas parciais ambíguas são rejeitadas e o `.part` fica preservado.
- Recuperação inicial registra contagens de itens interrompidos, reproduções pausadas, arquivos ausentes/corrompidos, órfãos e parciais preservados. Arquivos órfãos/parciais não são apagados automaticamente.
- Provedor autorizado continua pendente; registro vazio por padrão. Não existe integração offline ativa para YouTube e não será implementado contorno das restrições da plataforma.
- Miniatura e duração aparecem na lista de cache; progresso da visualização é atualizado a cada 5 segundos e exibido com posição/duração. Implementado, aguardando validação funcional na Fase 10.
- Central de Áudio permanece fora do diff. Nenhum build ou teste executado, conforme a regra de validar somente após concluir a implementação.

---

# PROGRESSO
- Fase atual: Fase 10 — Finalização (execução de ROADMAP_CORRECOES.md)
- Roadmap original: 75/76 submódulos (98,7%); o 10.8 (APK final) só fecha com T-703, validada pelo usuário
- Correções: 23/25 tarefas resolvidas (92,0%). T-001 concluída; T-304 encerrada; as demais IMPLEMENTADA (aguardando validação, nada compilado). Pendentes: T-702 (checklist, entregue aqui) e T-703 (APK, do usuário)
- Último ZIP: CDVirtual_CHECKPOINT_022.zip
- Bloqueios: compilar e validar exige Android SDK, rede e aparelho (indisponíveis aqui); a validação é do usuário
## Histórico
- 1.1 — projeto Gradle (Kotlin DSL), módulo :app, MainActivity mínima — CDVirtual_v001_1.1.zip
## ASSUMINDO
- minSdk 26, targetSdk/compileSdk 36 (T-602; era 34), AGP 8.5.2, Kotlin 1.9.24. Ambiente sem rede e sem Android SDK: build não verificado aqui.
- Gradle wrapper (T-102): scripts e gradle-wrapper.properties versionados; o gradle-wrapper.jar precisa estar presente (gerar com `gradle wrapper` em máquina com rede se faltar).
- 1.2 — Configurar Kotlin e Jetpack Compose — CDVirtual_v002_1.2.zip
- 1.3 — Criar navegação e identidade visual — CDVirtual_v003_1.3.zip
- 1.4 — Organizar estrutura de pacotes — CDVirtual_v004_1.4.zip
- 1.5 — Configurar permissões e armazenamento — CDVirtual_v005_1.5.zip
## ASSUMINDO (Fase 02)
- Room exige ao menos uma entidade: 2.1 cria TrackEntity mínima; 2.2 a expande. Banco ainda em versão 1 (pré-lançamento), sem migrações; campos adicionados durante a fase 02 alteram o schema v1.
- KSP 1.9.24-1.0.20, Room 2.6.1.
- 2.1 — Implementar Room / SQLite — CDVirtual_v006_2.1.zip
- 2.2 — Criar cadastro de músicas — CDVirtual_v007_2.2.zip
- 2.3 — Importar arquivos locais — CDVirtual_v008_2.3.zip
- 2.4 — Identificar metadados, duração e formato — CDVirtual_v009_2.4.zip
- 2.5 — Exibir capas — CDVirtual_v010_2.5.zip
- 2.6 — Implementar busca e exclusão — CDVirtual_v011_2.6.zip
- 2.7 — Identificar arquivos duplicados — CDVirtual_v012_2.7.zip
- 3.1 — Criar campo para inserir URLs — CDVirtual_v013_3.1.zip
- 3.2 — Identificar automaticamente a plataforma — CDVirtual_v014_3.2.zip
- 3.3 — Analisar o conteúdo do link — CDVirtual_v015_3.3.zip
- 3.4 — Identificar formatos e qualidades disponíveis — CDVirtual_v016_3.4.zip
- 3.5 — Implementar importação de links diretos — CDVirtual_v017_3.5.zip
- 3.6 — Criar conectores específicos para fontes compatíveis — CDVirtual_v018_3.6.zip
- 3.7 — Considerar YouTube, Facebook, Instagram e 4shared por meios permitidos — CDVirtual_v019_3.7.zip
- 3.8 — Implementar progresso, cancelamento e tratamento de erros — CDVirtual_v020_3.8.zip
- 4.1 — Analisar volume de cada música — CDVirtual_v021_4.1.zip
## ASSUMINDO (Fase 04)
- Análise e processamento de áudio 100% locais em Kotlin (MediaExtractor/MediaCodec + DSP próprio), sem dependência de FFmpeg nesta fase. FFmpeg/LAME entram na Fase 06 (codificação MP3).
- Banco: versão incrementada por mudança de schema com fallbackToDestructiveMigration (somente pré-lançamento).
- 4.2 — Normalizar volume percebido (LUFS) — CDVirtual_v022_4.2.zip
- 4.3 — Aplicar ganho automático — CDVirtual_v023_4.3.zip
- 4.4 — Evitar distorção e clipping — CDVirtual_v024_4.4.zip
- 4.5 — Permitir ajuste de volume individual — CDVirtual_v025_4.5.zip
- 4.6 — Oferecer equalizador básico de graves, médios e agudos — CDVirtual_v026_4.6.zip
- 4.7 — Permitir equalização automática do CD inteiro — CDVirtual_v027_4.7.zip
  - ASSUMINDO: "CD inteiro" = todas as faixas analisadas da biblioteca até o editor de CD (Fase 05). Schema Room v6 (destrutivo, pré-lançamento).
- 4.8 — Pré-visualização do resultado — CDVirtual_v028_4.8.zip
- 4.9 — Preservar arquivos originais — CDVirtual_v029_4.9.zip

## Fase 04 — CONCLUÍDA
- Validação: nenhuma suíte de testes existe no projeto; sem Android SDK/rede o build não roda aqui. Verificação estática: imports internos e referências conferidos, sem pendências. Compatibilidade: schema Room v6 (migração destrutiva, pré-lançamento).
- 5.1 — Criar novo CD — CDVirtual_v030_5.1.zip
  - ASSUMINDO: CdEntity mínima (id, name, createdAt, updatedAt), schema Room v7 destrutivo; faixas do CD serão tabela de junção na 5.3.
- 5.2 — Definir nome e capa — CDVirtual_v031_5.2.zip
  - ASSUMINDO: capa escolhida via SAF (image/*), reduzida a 1024 px e salva em JPEG no CoverStore; schema Room v8 destrutivo.
- 5.3 — Selecionar músicas da biblioteca — CDVirtual_v032_5.3.zip
  - ASSUMINDO: uma música aparece no máximo uma vez por CD (PK cdId+trackId); remover música da biblioteca remove-a dos CDs (FK CASCADE); schema v9 destrutivo.
- 5.4 — Reordenar músicas — CDVirtual_v033_5.4.zip
  - ASSUMINDO: reordenação por botões subir/descer (sem arrastar), sem alteração de schema.
- 5.5 — Remover músicas do CD — CDVirtual_v034_5.5.zip
- 5.6 — Calcular duração total — CDVirtual_v035_5.6.zip
- 5.7 — Estimar tamanho do arquivo final — CDVirtual_v036_5.7.zip
  - ASSUMINDO: estimativa = duração x 320 kbps CBR (ID3/capa ignorados).
- 5.8 — Salvar projetos de CD — CDVirtual_v037_5.8.zip
  - ASSUMINDO: projetos persistem no Room a cada edição; "salvar" = confirmar/atualizar data; último CD aberto restaurado via SharedPreferences.

## Fase 05 — CONCLUÍDA
- Sem suíte de testes nem Android SDK/rede: validação estática (imports/referências sem pendências). Schema Room v9 (migração destrutiva, pré-lançamento).
- 6.1 — Decodificar arquivos de áudio — CDVirtual_v038_6.1.zip
  - ASSUMINDO: decodificação via MediaCodec (PcmDecoder da Fase 04), sem FFmpeg; codificação MP3 (6.5/6.6) exigirá encoder LAME/FFmpeg local (verificar disponibilidade offline).
- 6.2 — Padronizar formato de áudio — CDVirtual_v039_6.2.zip
  - ASSUMINDO: formato padrão 44,1 kHz estéreo float; multicanal rebaixado (C/surround -3 dB, LFE descartado); DSP não validado por execução (sem testes nesta etapa).
- 6.3 — Aplicar normalização e equalização — CDVirtual_v040_6.3.zip
  - ASSUMINDO: faixa sem análise de loudness recebe ganho 0 dB (a exportação deverá aguardar a análise).
- 6.4 — Concatenar as músicas — CDVirtual_v041_6.4.zip
  - ASSUMINDO: faixas emendadas sem pausa (gapless); falha em qualquer faixa aborta o CD, nunca a omite.
- 6.5 — Gerar um único arquivo MP3 — CDVirtual_v042_6.5.zip
  - ASSUMINDO (RISCO DE BUILD): encoder LAME via `com.github.naman14:TAndroidLame:1.1` (JitPack, repositório restrito ao grupo em settings.gradle.kts). Coordenadas e API (LameBuilder/AndroidLame.encode/flush/close) vieram de memória, sem rede para conferir; se não resolverem, trocar somente media/encoder/LameMp3Encoder.kt (interface Mp3Encoder isola a biblioteca). LAME é LGPL.
  - ASSUMINDO: saída em arquivo interno do app (".part" -> rename atômico); pasta de destino SAF só na Fase 09.
- 6.6 — Exportar em 320 kbps CBR — CDVirtual_v043_6.6.zip
  - ASSUMINDO: LameBuilder com apenas setOutBitrate(320) produz CBR (padrão da biblioteca); por isso o resultado é sempre verificado por Mp3FrameScanner e rejeitado se não for 320 kbps CBR.
- 6.7 — Processar em segundo plano — CDVirtual_v044_6.7.zip
  - ASSUMINDO: serviço em primeiro plano tipo dataSync (targetSdk 34), wake lock parcial limitado a 3 h; faixas sem análise são analisadas antes de gerar; saída em filesDir/exports/<nome>.mp3.
- 6.8 — Cancelamento e recuperação — CDVirtual_v045_6.8.zip
  - ASSUMINDO: recuperação = limpar parciais ".part" e avisar que a geração foi interrompida (sem retomar do meio; o usuário gera de novo).
- 6.9 — Validar integridade do arquivo final — CDVirtual_v046_6.9.zip
  - ASSUMINDO: tolerância de duração 200 ms (atraso/preenchimento do LAME) e sondagem de decodificação de 2 s no início e no fim.

## Fase 06 — CONCLUÍDA
- Sem suíte de testes nem Android SDK/rede: validação estática (imports/referências sem pendências). PENDÊNCIA CRÍTICA para o primeiro build: confirmar a dependência LAME (TAndroidLame/JitPack) e a API em media/encoder/LameMp3Encoder.kt; o DSP (AudioStandardizer, Mp3FrameScanner) não foi executado.
- 7.1 — Gravar tags ID3 — CDVirtual_v047_7.1.zip
  - ASSUMINDO: ID3v2.3 próprio (sem biblioteca), UTF-16 com BOM; tag gravada em arquivo novo (.tagged) após o áudio e validada antes do rename. 7.1 grava TIT2 (nome do CD) e TYER.
- 7.2 — Definir álbum — CDVirtual_v048_7.2.zip
  - ASSUMINDO: álbum = nome do CD (TALB), igual ao título da faixa única.
- 7.3 — Definir artista e capa — CDVirtual_v049_7.3.zip
  - ASSUMINDO: artista é opcional (CdEntity.artist, schema Room v10 destrutivo) e vai em TPE1 e TPE2; sem artista, os quadros são omitidos. Capa = a capa do CD (JPEG do CoverPrepare) em APIC tipo 3 (frontal), omitida se ausente/ilegível/não-JPEG/maior que 8 MB.
- 7.4 — Definir duração — CDVirtual_v050_7.4.zip
  - ASSUMINDO: TLEN em milissegundos (ISO-8859-1) com a duração real concatenada (ConcatResult.totalMs), preenchida pelo Mp3Exporter; omitida se 0.
- 7.5 — Posição/tempo das faixas — CDVirtual_v051_7.5.zip
  - ASSUMINDO: posições gravadas como capítulos ID3 (CHAP + CTOC ordenado, um por faixa, tempos reais de CdConcatenator.TrackSpan em ms, título da faixa). Players sem suporte a CHAP os ignoram; a navegação por tempos da Fase 08 usará os mesmos spans. Não executado (sem testes nesta etapa).
- 7.6 — CUE opcional — CDVirtual_v052_7.6.zip
  - ASSUMINDO: CUE só é gerado se a opção "Gerar também arquivo CUE" estiver ligada (padrão desligada); gravado ao lado do MP3 em filesDir/exports/<nome>.cue (UTF-8 com BOM, INDEX 01 em mm:ss:ff a 75 fps); falha ao gravar o CUE não invalida o MP3. Cópia/exportação para a pasta do usuário fica na 9.3.

## Fase 07 — CONCLUÍDA
- Sem suíte de testes (nenhum diretório src/test) nem Android SDK/rede: validação estática (símbolos novos referenciados e importados, sem pendências). Schema Room v10 (migração destrutiva, pré-lançamento). Tags gravadas: TIT2, TALB, TPE1, TPE2, TYER, TLEN, APIC, CHAP/CTOC. DSP/ID3 não executados.
- 8.1 — Reproduzir — CDVirtual_v053_8.1.zip
  - ASSUMINDO: player sobre android.media.MediaPlayer (framework; sem Media3/ExoPlayer para não adicionar dependência não verificável offline); singleton media/playback/Mp3Player; a tela Player lista filesDir/exports/*.mp3. Segundo plano/notificação (8.7/8.8) exigirão mover o player para um serviço com MediaSession (framework).
- 8.2 — Pausar e continuar — CDVirtual_v054_8.2.zip
  - ASSUMINDO: ao continuar uma faixa terminada, recomeça do início.
- 8.3 — Avançar e retroceder — CDVirtual_v055_8.3.zip
  - ASSUMINDO: avançar/retroceder = salto de ±15 s dentro do MP3 único; troca de faixa (capítulo) fica na 8.10.
- 8.4 — Exibir nome atual — CDVirtual_v056_8.4.zip
  - ASSUMINDO: nome atual = título do capítulo ID3 (CHAP) que contém a posição, lido do próprio MP3 (Id3Reader, offline, sem depender do banco); sem capítulos, mostra o nome do arquivo. Posição atualizada a cada 500 ms na tela. Leitor não executado (sem testes).
- 8.5 — Exibir capa — CDVirtual_v057_8.5.zip
  - ASSUMINDO: capa lida do quadro APIC do próprio MP3 (Id3Reader.readCover, frontal preferida) e reduzida a ≤512 px para exibição; sem capa, nada é mostrado.
- 8.6 — Barra de progresso — CDVirtual_v058_8.6.zip
  - ASSUMINDO: Slider do Material3 com posição/duração; o salto ocorre ao soltar.
- 8.7 — Reprodução em segundo plano — CDVirtual_v059_8.7.zip
  - ASSUMINDO: PlaybackService em primeiro plano (tipo mediaPlayback, permissão FOREGROUND_SERVICE_MEDIA_PLAYBACK) iniciado ao tocar e encerrado quando o player é liberado; MediaPlayer com wake lock parcial (setWakeMode). Notificação mínima com ação "Parar"; controles/MediaSession na 8.8.
- 8.8 — Notificação com controles — CDVirtual_v060_8.8.zip
  - ASSUMINDO: Notification.MediaStyle do framework (sem androidx.media), ações Voltar 15 s / Pausar-Continuar / Avançar 15 s / Parar, título = faixa atual (atualizado a cada 2 s tocando), toque abre o app. Sem MediaSession ainda (8.9).
- 8.9 — Bluetooth — CDVirtual_v061_8.9.zip
  - ASSUMINDO: compatibilidade via android.media.session.MediaSession do framework (AVRCP/botões de fone e volante, metadados título/álbum/duração/capa), foco de áudio (pausa em perda, ducking, retomada) e pausa em ACTION_AUDIO_BECOMING_NOISY. Próximo/anterior mapeados provisoriamente para ±15 s; a 8.10 os passa a pular de faixa. Não testado com dispositivos reais.
- 8.10 — Navegação por tempos — CDVirtual_v062_8.10.zip
  - ASSUMINDO: navegação por faixas = capítulos ID3 (CHAP) do MP3; lista de faixas com tempo (toque salta e continua), botões ⏮/⏭ e próximo/anterior de fones/Bluetooth; "anterior" volta ao início da faixa se passaram mais de 3 s. Sem capítulos, próximo/anterior de fones caem para ±15 s.

## Fase 08 — CONCLUÍDA
- Sem suíte de testes nem Android SDK/rede: validação estática (símbolos/imports/manifest conferidos). Player baseado em MediaPlayer/MediaSession/Notification.MediaStyle do framework (sem novas dependências). Reprodução, background, notificação e Bluetooth NÃO foram exercitados em dispositivo.
- 9.1 — Escolher pasta de destino — CDVirtual_v063_9.1.zip
  - ASSUMINDO: pasta via OpenDocumentTree com permissão de leitura+escrita persistida (StorageAccess.outputFolder); core/storage/OutputFolder valida permissão/existência e descreve a pasta; trocar/esquecer libera a permissão antiga. A cópia do MP3 para a pasta é a 9.2.
- 9.2 — Exportar MP3 — CDVirtual_v064_9.2.zip
  - ASSUMINDO: cópia (não movimentação) de filesDir/exports/*.mp3 para a pasta SAF; nunca sobrescreve (usa "nome (n).mp3"); parcial apagado em falha/cancelamento; tamanho conferido quando o provedor informa. O original interno é mantido (gerenciado/excluído em 9.5/9.6).
- 9.3 — Exportar CUE — CDVirtual_v065_9.3.zip
  - ASSUMINDO: o CUE é gerado na hora a partir das tags do MP3 (CHAP/TALB/TPE1), independente da opção da 7.6, e gravado na pasta de destino com o mesmo nome base do MP3 exportado nesta sessão (se o MP3 ainda não foi exportado, usa o nome do arquivo interno). MP3 sem capítulos não gera CUE.
- 9.4 — Compartilhar — CDVirtual_v066_9.4.zip
  - ASSUMINDO: compartilhamento via androidx.core FileProvider (já incluso no core-ktx; sem nova dependência) limitado a filesDir/exports; ACTION_SEND (MP3) ou ACTION_SEND_MULTIPLE (MP3 + .cue interno, se existir ao lado). Fora do app não há CUE gerado na hora.
- 9.5 — Gerenciar CDs gerados — CDVirtual_v067_9.5.zip
  - ASSUMINDO: "CDs gerados" = MP3 únicos em filesDir/exports; o gerenciador lista título/artista/faixas/duração/tamanho/data/CUE (lidos das tags ID3), espaço total e oferece exportar, CUE, compartilhar, ouvir e renomear (MP3 + CUE juntos, sem sobrescrever). Exclusão é a 9.6.
- 9.6 — Excluir exportados — CDVirtual_v068_9.6.zip
  - ASSUMINDO: excluir = apagar o MP3 gerado (+ CUE interno) de filesDir/exports, sempre com confirmação; opcionalmente apaga também as cópias gravadas na pasta de destino NESTA sessão (o app só conhece os documentos que ele próprio criou; cópias de sessões anteriores devem ser apagadas pelo usuário). Música original e biblioteca nunca são tocadas.

## Fase 09 — CONCLUÍDA
- Sem suíte de testes nem Android SDK/rede: validação estática (símbolos/imports/manifest/FileProvider conferidos). SAF, FileProvider e exclusão não exercitados em dispositivo.
- 10.1 — Correções — CDVirtual_v069_10.1.zip
  - ASSUMINDO: revisão estática (sem SDK/rede/testes). Corrigido: (1) ExportService chama startForeground em todo caminho de ACTION_START, inclusive início duplicado/intent inválida (evita ForegroundServiceDidNotStartInTimeException); (2) ExportService.start marca Running no pedido e volta a Failed se o sistema recusar o serviço; (3) allowBackup=false (backup automático tentaria incluir MP3 de centenas de MB e as permissões SAF não são restauradas); (4) TrackImporter não converte cancelamento em "arquivo inválido". Pendência crítica da 6.5 (dependência LAME) segue exigindo build com internet.
- 10.2 — Memória — CDVirtual_v070_10.2.zip
  - ASSUMINDO: o pipeline de áudio (decodificação, padronização, ganho/EQ, concatenação, LAME, validação, ID3 prepend) já é em fluxo, com memória constante por bloco; o consumo relevante estava na leitura de tags. Id3Reader agora percorre os quadros com seek e lê só os corpos pedidos (texto/capítulos nunca carregam a capa de até 8 MB; a capa não carrega o resto); GeneratedCds.describe e CueSheet.fromMp3 usam uma única leitura (Id3Reader.readInfo, antes 4). Listagem de CDs gerados e decodificação da capa do player passaram para Dispatchers.IO. Não medido em dispositivo (sem SDK/testes). Capa do PlaybackService segue na thread principal (uma vez por arquivo, ≤512 px).
- 10.3 — Arquivos grandes — CDVirtual_v071_10.3.zip
  - ASSUMINDO: 320 kbps CBR ≈ 144 MB/h; todos os tamanhos/posições do pipeline já são Long (CHAP/TLEN em ms de 32 bits cobrem ~49 dias). ExportService verifica espaço livre antes de gerar (necessário = estimado × 2 + MP3 antigo, se for regerar, + 64 MB, pois ".part" e ".tagged" coexistem até a validação) e falha cedo com mensagem legível. Cópia SAF: se falhar com arquivo > 4 GiB, a mensagem cita o limite do FAT32. Editor de CD avisa quando a estimativa passa de 4 GB. Wake lock de 3 h mantido (decodificação/codificação rodam muito acima do tempo real). Não exercitado com arquivos reais (sem dispositivo/testes).
- 10.4 — Qualidade — CDVirtual_v072_10.4.zip
  - ASSUMINDO: qualidade de áudio revisada sem ouvir/medir (sem dispositivo/testes). (1) Conversão float→PCM16 do encoder agora usa dither TPDF (Pcm16Quantizer, xorshift determinístico; zero exato permanece zero) em vez de arredondamento simples; (2) ThreeBandEqualizer sem alocação por amostra (laços indexados em vez de iterador de lista), o que evita GC contínuo em CDs longos. Mantidos: reamostragem sinc 2048 fases/32 taps, ganho limitado por true peak (já descontando o maior reforço de EQ), clip de segurança ±1. NÃO alterado: ajuste de qualidade do LAME (setQuality) — API não verificável offline; o padrão da biblioteca é aceitável a 320 kbps.
- 10.5 — Interrupções — CDVirtual_v073_10.5.zip
  - ASSUMINDO: já cobertos antes (6.8/8.9): geração interrompida (marcador + limpeza de ".part"/".tagged"), ligação/outro app durante a reprodução (foco de áudio: pausa e retomada; ducking), fones/Bluetooth desconectados. Acrescentado: (1) MainActivity com configChanges, para girar a tela/mudar tema/idioma não recriar a Activity e cancelar a cópia SAF em andamento; (2) pré-visualização (4.8) pede foco de áudio transitório e para ao perdê-lo (o player do CD pausa e volta sozinho); (3) ImportRecovery remove ".part" de downloads interrompidos ao abrir o app; (4) DirectDownloader recusa download incompleto (conexão cortada antes de Content-Length); (5) mensagens claras para queda de conexão (ConnectException/SocketException/EOFException). Limitações registradas: cópia SAF interrompida por morte do processo pode deixar documento parcial na pasta de destino (o app não consegue listá-lo depois); o botão Continuar da tela do player não pede foco de áudio (só a sessão de mídia/notificação pedem). Android 15 impõe timeout em serviços dataSync (targetSdk 35); o projeto usa targetSdk 34.
- 10.6 — Players externos — CDVirtual_v074_10.6.zip
  - ASSUMINDO: compatibilidade pelo próprio arquivo, sem testar em players reais (sem dispositivo): MPEG-1 Layer III 44,1 kHz 320 kbps CBR, ID3v2.3 (UTF-16 com BOM, TIT2/TALB/TPE1/TPE2/TYER/TLEN/APIC JPEG frontal/CHAP+CTOC) — players sem suporte a capítulos os ignoram e a duração vem de TLEN. Acrescentado: botão "Abrir em outro player" (ShareFiles.openWith: ACTION_VIEW audio/mpeg via FileProvider, somente leitura, com ClipData para a permissão chegar ao app escolhido) e CUE com números sempre ASCII (Locale.ROOT; em idiomas com outros dígitos o CUE ficaria ilegível para outros players). NÃO implementado: quadro Xing/Info do LAME (duração exata/gapless em players que o exigem) — exigiria construir um quadro binário sem como validar offline; players estimam a duração de CBR pelo tamanho e TLEN cobre os demais.
- 10.7 — Sem internet — CDVirtual_v075_10.7.zip
  - ASSUMINDO: auditoria de código — a única classe que abre conexões é o importador por link (Http/LinkAnalyzer/DirectDownloader); biblioteca, análise, EQ, editor, geração do MP3, player e exportação não dependem de rede. Acrescentado: permissão ACCESS_NETWORK_STATE e NetworkStatus.isOnline (exige capacidade INTERNET da rede ativa; se não for possível saber, tenta mesmo assim); o importador avisa na hora, com mensagem clara, ao analisar/baixar sem conexão, e a tela informa que é o único recurso online e aponta "Importar músicas" (Biblioteca) para arquivos locais. Sem dependências que baixem conteúdo em tempo de execução (LAME vem empacotado no APK pela dependência Gradle). Não exercitado em dispositivo (modo avião).
- 10.8 — APK final (PREPARAÇÃO, não concluído) — CDVirtual_v076_10.8-prep.zip
  - versionName 1.0.0; assinatura de release via keystore.properties (opcional); ícone vetorial; docs/BUILD.md com passos e roteiro manual. minify desligado por causa do LAME (JNI). Pendente: compilar e testar em aparelho.

## ASSUMINDO (T-103)
- v10 = linha de base da 1.0; v1–v9 (desenvolvimento) ainda recriam o banco. app/schemas/.../10.json será gerado no primeiro build e precisa ser versionado pelo usuário.

## ASSUMINDO (T-303)
- "CD inteiro" (4.7) = faixas do CD escolhido na tela Equalizador (padrão: último CD do editor); sem CD, biblioteca inteira, com rótulo explícito. EQ segue por faixa: faixa em dois CDs compartilha a EQ (P-032 depende de T-001).

## ASSUMINDO (T-205)
- Excluir CD remove só o projeto (cascade em cd_tracks, sem mudança de schema); capa órfã é apagada; músicas, originais e MP3 gerados ficam.

## ASSUMINDO (T-602)
- Distribuição pela Google Play (decisão do usuário, 15/11/2026): targetSdk/compileSdk 36. AGP 8.5.2 pode exigir atualização; ver docs/BUILD.md. A nota do 10.5 sobre timeout em serviços dataSync é tratada por `ExportService.onTimeout`.

## Correções (ROADMAP_CORRECOES.md)
- T-001 CONCLUÍDA (P-015 não exigido, P-030 exigido, P-031 Play/API 36, P-032 por faixa); T-304 ENCERRADA.
- T-101…T-103, T-201…T-205, T-301…T-303, T-401, T-402, T-501…T-505, T-601, T-602 IMPLEMENTADAS (aguardando validação). T-701 documentação feita; T-702 checklist em docs/CHECKLIST_VALIDACAO.md; T-703 do usuário.

## ASSUMINDO (T-801)
- Nome "MixTape MP3" escolhido pelo usuário, sem checagem de disponibilidade na Play. Tela de boas-vindas com tempo fixo de 2,5 s (toque pula). Ícone desenhado em vetor, não visto em aparelho.

## ASSUMINDO (11.1)
- Transporte escolhido: Innertube direto (zero dependência nova, sem GPL). `YouTubeApi.kt` é o ponto de troca; Cobalt e NewPipe Extractor ficam como alternativas sem mexer no conector.
- Sem resolução do parâmetro `n`: velocidade de download pode ser limitada. Sem suporte a playlist, canal e transmissão ao vivo.
- E2E de emulador não cobre YouTube (rede instável); a validação é manual, em `docs/CHECKLIST_VALIDACAO.md`.

## ASSUMINDO (integração 2026-11-16)
- Base = `feat/youtube-connector`; do snapshot anterior entram só Configurações, gênero/abas da Biblioteca, cópia automática, apagar originais, ScreenScaffold/ButtonFlow, botão Voltar e controle de volume do aparelho. Tema, ícone, boas-vindas, Início em grade, CI, `settings.gradle.kts` e todo o código de downloads/navegador/YouTube ficam como na base.
- A opção global "Normalizar volume" da tela Configurações vale para a tela Volume e equalização; no MP3 final vale a opção por playlist (`CdAudioSettings.normalize`), como já era na base.
- Room v11: `11.json` ainda não existe (é gerado pelo KSP no primeiro build e deve ser versionado).
- Nada compilado: sem Android SDK, Gradle nem rede neste ambiente.



## Roadmap Central de Vídeos e Cache Offline (iniciado em 2026-11-26)
- Repositório: `Kyra2214/MixTapeMp3`; branch `main`; baseline `1a80446cf53485dabd56057c0fe385e2e9a5ae24`.
- Fase 0 (auditoria e baseline): concluída por inspeção estática.
- Fase 1 (independência das centrais): arquitetura existente confirmada; documentação e consolidação pendentes.
- Fases 2–9: pendentes.
- Fase 10 (testes/build/validação): reservada para depois da implementação integral.
- Percentual dos submódulos de implementação deste roadmap: 0%; não confundir auditoria concluída com implementação.
- Bloqueio: não foi confirmada uma via autorizada para download/cache persistente de vídeos do YouTube. A implementação deve respeitar os mecanismos oficiais da fonte.
- Nenhum teste, build ou validação foi executado nesta etapa.

- 2026-11-26 — Fase 1.1 do roadmap de cache: a reprodução de prévia acionada na Central de Áudio agora chama o serviço com `recordHistory=false`; o serviço deixa de gravar histórico e posição para essa origem. O player continua compartilhado para preservar a reprodução nativa já existente. Validação transferida à fase final; nenhum teste/build executado.

- 2026-11-26 — Fase 1.2: criado `core/storage/MediaStorageLayout.kt`; importador e recuperação de áudio passaram a usar `audioImportsDir`, preservando o caminho existente para compatibilidade. Diretório de vídeo reservado em `videoCacheDir`. Nenhum teste/build executado; validação final pendente.

- 2026-11-26 — Fase 2.1: entidade `VideoCacheEntity`, DAO `VideoCacheDao`, estados/qualidades e migração Room v13→v14 implementados. Schema 14 não foi gerado manualmente; depende do KSP no build da fase final. Nenhum teste/build executado.

- 2026-11-26 — Fase 2.2: criado `VideoCacheRepository` e exposto em `CDVirtualApp`; valida qualidade/estado e separa persistência de manipulação de arquivos. Correção de sintaxe na SQL da migração v13→v14. Nenhum teste/build executado.

- 2026-11-26 — Fase 2.3: máquina de estados e armazenamento seguro do cache implementados. Arquivos `.part` só são publicados após validação; estado READY exige arquivo válido dentro de `video_cache`. Recuperação de parciais continua pendente. Nenhum teste/build executado.

- 2026-11-26 — Fase 2.4: `VideoCacheRecovery` integrado à inicialização; recupera estados, valida arquivos, preserva parciais e reporta órfãos sem apagá-los. Nenhum teste/build executado.

- 2026-11-26 — Fase 3.1: `VideoCacheManager` e `VideoCacheSettingsStore` implementados; limite padrão 3, presets 1/2/3/5/10 e personalizado 1–100; reserva, capacidade, remoção manual e exclusão após conclusão natural. Nenhum teste/build executado.

- 2026-11-26 — Reforçada a reserva de vagas com Mutex para impedir ultrapassagem concorrente do limite configurado. Sem testes/build, conforme execução definida.

## 2026-11-26 — Implementação do cache temporário na Central de Vídeos
- Implementados: motor de transferência próprio para vídeo; retomada HTTP Range; `.part` preservado; registro de provedores autorizados; checagem de espaço conhecido; publicação para READY; integração de reprodução local Media3; posição/estado; exclusão natural/manual; seção Cache na UI.
- Limitação: registro de provedores inicia vazio. Não há provedor YouTube oficialmente autorizado confirmado para download/armazenamento local; a reprodução online segue inalterada.
- Pendências: integração de fonte autorizada real; UI de progresso e seletor de qualidade; validação de contêiner/arquivo; tratamento adicional de concorrência e retomada da MediaSession; testes e builds na Fase 10.
- Regra de escopo cumprida: nenhuma alteração em arquivos da Central de Áudio.
- Nenhum teste, build ou validação executado.
- Robustez adicional: retomada de itens locais via MediaSession; posição salva periodicamente; seek manual até o fim não apaga o vídeo e reinicia a posição; validação estrutural com `MediaExtractor`; rollback de promoção preserva o parcial quando a persistência Room falha.

- Transporte reforçado contra downgrade HTTPS e resposta com MIME divergente; operações de arquivo e análise MediaExtractor movidas para Dispatchers.IO para evitar bloqueio da interface.

## Central de Vídeos — Cache temporário (branch feature/video-temporary-cache)
- Núcleo de transferência HTTPS, retomada HTTP Range, preservação de .part e validação de mídia implementados.
- Gerenciador, recuperação, player Media3 offline e seção Cache implementados.
- Robustez adicional: exclusões natural/manual serializadas; conclusão limpa arquivo final e parcial; recuperação repete limpeza de resíduos concluídos.
- Não há provedor autorizado registrado; download offline de YouTube permanece bloqueado até uma via permitida. Reprodução online preservada.
- Pendentes: integração de provedor permitido, seletor de qualidade por vídeo, progresso visual, estados de erro/armazenamento e validação final.
- Builds/testes não executados por determinação do roadmap: somente após a implementação completa.
- Regra de escopo respeitada: nenhuma alteração em arquivos da Central de Áudio.

### Continuação — UI e concorrência do cache de vídeos
- Interface de preparação com seletor 240p/360p/480p, progresso em bytes e barra de progresso; controles só aparecem com provedor autorizado registrado.
- Coordenador com limite de 3 transferências simultâneas por padrão e bloqueio de duplicidade por cacheId.
- Provedor YouTube permanece desativado até existir integração que autorize expressamente o armazenamento local.
- Sem build/testes nesta etapa, conforme instrução de executar validação somente ao fim do roadmap.


### Continuação — cancelamento da preparação offline
- O diálogo de preparação agora permite cancelar a transferência em andamento.
- O cancelamento propaga `CancellationException` ao coordenador, que preserva o `.part` e registra o item como interrompido; a retomada depende do suporte HTTP Range da fonte autorizada.
- O roadmap foi atualizado: progresso visual do download, lista vazia e mensagens de falha/espaço/fonte incompatível já possuem UI; progresso da visualização e miniatura/duração seguem pendentes.
- Nenhum build/teste executado; a Central de Áudio permanece fora do escopo.

### Ajuste de escopo e feedback de cancelamento
- Removida qualquer previsão de importação de vídeos locais: essa funcionalidade não faz parte do produto.
- Diálogo de preparação agora exibe uma mensagem informativa após cancelamento, esclarecendo a preservação do parcial quando a transferência já havia iniciado.
- Cache permanece restrito a provedores remotos com autorização expressa de armazenamento temporário.

### Robustez do motor de cache
- Recuperação de ausentes/corrompidos implementada com preservação e reporte de órfãos.
- Validação de Content-Range reforçada para retomada HTTP.
- Exclusão manual agora persiste REMOVED antes da operação física, para retry seguro após interrupção.
- Ação de apagar desabilitada durante fila/transferência ativa.
- Ainda sem build ou testes, conforme ordem definida: validação somente após concluir o roadmap.

### Serviço de transferência em primeiro plano
- Serviço dedicado da Central de Vídeos registrado no Manifest com tipo `dataSync`.
- Notificação de progresso e ação para cancelar transferências ativas.
- UI delega o trabalho ao serviço, sem manter o download preso ao ciclo de vida da tela.
- Cancelamento inclusive durante espera pelo semáforo persiste INTERRUPTED.
- O serviço de áudio existente não foi reutilizado nem alterado.

- Exclusões manuais que falham permanecem registradas como REMOVED e visíveis para nova tentativa; recuperação repete a limpeza no próximo início.

- Reservas QUEUED abandonadas por encerramento do processo são recuperadas como INTERRUPTED; arquivos parciais permanecem intactos.

- Qualidades disponíveis são consultadas no provedor por vídeo; UI não oferece opções que a fonte não confirmou.
- Reserva concorrente de espaço implementada para downloads com tamanho conhecido, ajustada conforme bytes recebidos.

- Recuperação reconcilia arquivo já promovido com estado DOWNLOADING, concluindo READY sem baixar novamente; `completedAt` é persistido ao marcar conclusão natural.

- Validação HTTP Range reforçada para rejeitar respostas 206 incompletas/incoerentes antes da publicação.
- Recuperação evita validar duas vezes o mesmo arquivo e conclui publicação que ficou entre rename e commit Room.
- Reserva de espaço estimado compartilhada entre transferências concorrentes.

- Cancelamento antes do início do HTTP, inclusive durante a reserva de espaço, também deixa o item INTERRUPTED e retomável.

- UI da seção Cache agora permite retomar itens INTERRUPTED pelo provedor registrado, mantendo a qualidade original e reaproveitando parcial compatível.

- Notificação do serviço de primeiro plano exibe barra de progresso (ou indeterminada), resultado final de falha/cancelamento e ação de cancelar.

- Arquivos corrompidos mantêm o caminho gerenciado para remoção manual; o coordenador bloqueia uma nova publicação sobre a cópia inválida até o usuário removê-la.
- Arquivo publicado íntegro em estado INTERRUPTED é restaurado para READY ao retomar, sem transferir novamente.

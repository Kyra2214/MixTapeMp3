# ROADMAP — CENTRAL DE VÍDEOS E CACHE OFFLINE

**Projeto:** MixTapeMp3  
**Base:** branch `main`  
**Commit de referência:** `1a80446cf53485dabd56057c0fe385e2e9a5ae24`  
**Escopo:** evolução da Central de Vídeos, preservando a Central de Áudio.

## Princípios obrigatórios

1. A Central de Áudio e a Central de Vídeos são experiências distintas.
2. A biblioteca de áudio contém apenas faixas de áudio importadas; histórico, canais e estado de vídeos pertencem ao domínio de vídeos.
3. Componentes técnicos podem ser compartilhados, mas bibliotecas, filas, históricos, preferências e arquivos gerenciados devem permanecer isolados.
4. Não reescrever o player existente sem necessidade comprovada.
5. Preservar reprodução em segundo plano, tela cheia, retomada e controles existentes.
6. O cache é temporário, mas sobrevive a fechar o app, trocar de aba, pausar, perder conexão ou reiniciar o aparelho; só é apagado após conclusão natural da visualização ou ação manual.
7. Exclusão automática somente após conclusão real da visualização; exclusão manual sempre disponível.
8. Qualidade por vídeo: 240p (padrão), 360p e 480p. Não oferecer HD/Full HD.
9. Não remover cache existente automaticamente para abrir espaço. Limite atingido deve gerar aviso e ação explícita do usuário.
10. A preparação offline temporária deve usar somente fontes e mecanismos que autorizem download e armazenamento local. Não implementar extração, captura, gravação ou cache temporário de streams do YouTube fora dos mecanismos oficiais autorizados. A implementação de cache para vídeos do YouTube depende de uma via oficialmente permitida; uso pessoal não elimina as restrições da fonte.

## FASE 0 — PREPARAÇÃO E AUDITORIA

### 0.1 Identidade do repositório e baseline
- [x] Repositório confirmado: `Kyra2214/MixTapeMp3`.
- [x] Branch principal confirmada: `main`.
- [x] Commit de referência registrado: `1a80446cf53485dabd56057c0fe385e2e9a5ae24`.
- [x] Permissões de leitura e escrita confirmadas.
- [x] README, roadmap existente, changelog, progresso, arquitetura e instruções de build examinados.
- [x] Estrutura de arquivos e estado Git remoto examinados. A API do GitHub não expõe alterações locais não commitadas; nenhuma alteração remota pendente foi indicada.

### 0.2 Auditoria de separação
- [x] Home navega para destinos distintos: Central de Vídeos (`Destination.YouTube`) e Central de Áudios (`Destination.Browser`).
- [x] Biblioteca musical usa `TrackEntity/TrackDao/TrackRepository`.
- [x] Histórico de vídeos usa `YouTubeHistoryEntity/YouTubeHistoryDao`.
- [x] Canais salvos usam `YouTubeChannelEntity/YouTubeChannelDao`.
- [x] Reprodução MP3 usa `Mp3Player/PlaybackService`; reprodução YouTube usa `YouTubePlaybackService`.
- [x] A pesquisa YouTube no Browser é uma fonte do fluxo de áudio; isso não deve registrar o vídeo-fonte como item da biblioteca musical. O item importado é o arquivo de áudio produzido, não o vídeo.
- [ ] Validação funcional de não contaminação: transferida para a fase final, conforme política de execução.

**Resultado da auditoria:** a separação estrutural já existe em boa parte. Não foi encontrada evidência estática de que `TrackDao.observeAll/search` leia as tabelas de histórico/canais do YouTube. O histórico de vídeos é separado. Não fazer refatoração cosmética nem remover o modo “Só áudio” do player de vídeos sem requisito explícito.

## FASE 1 — CONSOLIDAR A INDEPENDÊNCIA DAS CENTRAIS

### 1.1 Domínios e responsabilidades
- [x] Mapear destinos, telas, bancos, repositórios e serviços de reprodução.
- [x] Confirmar que áudio e vídeo usam persistência e serviços distintos.
- [x] Documentar contratos e fronteiras dos dois domínios.
- [x] Garantir que o resultado de uma extração de áudio seja cadastrado somente como áudio.
- [x] Garantir que a prévia de vídeo acionada no navegador da Central de Áudio não grave histórico/progresso na Central de Vídeos.
- [x] Garantir que histórico/canais de vídeos nunca sejam consultados pela biblioteca MP3.
- [ ] Transferir testes de regressão e validação funcional para a fase final.

### 1.2 Armazenamento
- [x] Definir diretórios próprios para importações de áudio e cache de vídeo permitido em `MediaStorageLayout`, preservando o caminho legado `filesDir/imports`.
- [x] Garantir que a recuperação/limpeza de importações de áudio percorra exclusivamente `audioImportsDir`.
- [ ] Garantir que limpeza de cache de vídeo nunca remova MP3 ou arquivos originais do usuário (depende do gerenciador da Fase 3; validar na Fase 10).

## FASE 2 — MODELO E PERSISTÊNCIA DO CACHE

- [x] Criar entidade exclusiva `VideoCacheEntity` para itens de cache de vídeo, sem reutilizar `TrackEntity`.
- [x] Criar `VideoCacheDao` exclusivo para consulta e persistência de metadados.
- [x] Criar migração Room explícita v13→v14; exportação do schema 14 fica pendente para a fase final de build.
- [x] Criar `VideoCacheRepository` próprio, exposto por `CDVirtualApp`, com validação de qualidade/estado e operações de metadados.
- [x] Persistir identificador da fonte, título, miniatura, duração, qualidade, tamanho, caminho, estado, posição e datas em `VideoCacheEntity`.
- [x] Modelar estados: aguardando, baixando, interrompido, pronto, em reprodução, pausado, concluído e removido; `VideoCacheStateMachine` valida transições.
- [x] Garantir recuperação consistente após encerramento inesperado por `VideoCacheRecovery` (downloads viram INTERRUPTED, reprodução válida vira PAUSED, arquivos ausentes são sinalizados e conclusões pendentes são limpas).
- [x] Não marcar conteúdo remoto como offline disponível antes da confirmação de arquivo completo e íntegro: `VideoCacheFileStore` publica `.part` atomicamente e `markReadyAfterPromotion` valida o arquivo.

## FASE 3 — GERENCIADOR DE CACHE

- [x] Criar módulo de gerenciamento do cache de vídeo.
- [x] Definir limite configurável: 1, 2, 3, 5, 10 ou personalizado.
- [x] Impedir remoção automática de itens prontos para liberar vaga.
- [x] Expor espaço ocupado e espaço livre.
- [x] Suportar remoção manual.
- [x] Recuperar estados e arquivos após reinicialização.
- [x] Identificar arquivos órfãos sem apagar arquivos válidos não assistidos; parciais e arquivos órfãos são reportados, nunca removidos automaticamente.
- [x] Criar contrato e registro explícito de provedores autorizados; o registro inicia vazio e não habilita extração automática do YouTube.

## FASE 4 — PREPARAÇÃO OFFLINE E QUALIDADE

- [ ] Implementar adaptador de fonte para conteúdo que autorize download e armazenamento local (provedor real autorizado continua pendente).
- [x] Seletor individual 240p (padrão), 360p e 480p na interface de preparação; a fonte deve confirmar a qualidade solicitada. Integração funcional depende de provedor autorizado.
- [x] Exibir somente qualidades realmente disponíveis, consultadas no contrato do provedor e filtradas para 240p/360p/480p.
- [x] Núcleo de transferência próprio da Central de Vídeos com progresso, cancelamento preservando `.part` e retomada HTTP Range quando suportada; valida limites e tamanho de respostas HTTP 206.
- [x] Serviço de primeiro plano exclusivo da Central de Vídeos, com notificação de progresso e cancelamento, permitindo continuar transferências fora da tela. O serviço usa o coordenador com limite de três transferências, trata o timeout de execução `dataSync` do Android e não reutiliza o serviço de downloads de áudio.
- [x] Gravar em `.part` e promover atomicamente após conclusão e validação de tamanho.
- [x] Validar contêiner e existência de faixa de vídeo via `MediaExtractor` antes de marcar READY.
- [x] Verificar espaço disponível quando a fonte informa o tamanho total e reservar capacidade entre preparações concorrentes para evitar supercompromisso de espaço.
- [x] Não implementar downloader ou cache temporário de streams do YouTube por métodos não autorizados. A integração específica com YouTube permanece bloqueada até existir mecanismo oficialmente permitido para esse uso.

## FASE 5 — REPRODUÇÃO OFFLINE

- [x] O serviço Media3 reproduz arquivo local válido quando iniciado por um item do cache.
- [x] Reprodução local pelo Media3 sem depender da URL remota após o arquivo estar pronto.
- [x] Persistir posição e estado durante a reprodução do cache.
- [x] Retomar item pronto/pausado pelo cache; retomada de downloads parciais fica disponível no núcleo HTTP quando a fonte suporta Range.
- [x] Reutilizar o serviço Media3 e a MediaSession existentes para arquivos locais, sem criar outro player.
- [x] Seek manual até o fim não autoriza exclusão automática.

## FASE 6 — REGRA DE EXCLUSÃO

- [x] Excluir automaticamente somente após conclusão natural confirmada pelo player; seek manual até o fim preserva o arquivo.
- [x] Manter arquivo ao pausar, sair do vídeo, trocar de aba, fechar o app, reiniciar aparelho ou perder conexão.
- [x] Permitir exclusão manual pela seção Cache da Central de Vídeos.
- [x] Remoção deve ser idempotente e sincronizar arquivo e banco (mutex serializa exclusões; arquivo final e parcial são removidos antes do registro).
- [x] Nunca apagar cache não assistido durante limpeza de recuperação.

## FASE 7 — INTERFACE DA CENTRAL DE VÍDEOS

- [x] Criar seção Cache temporário dentro da Central de Vídeos, independente da biblioteca de áudio.
- [x] Exibir título, miniatura, duração, qualidade, tamanho e estado na listagem do cache.
- [x] Exibir progresso do download (barra e bytes recebidos/total) e progresso de visualização com posição/duração persistidas.
- [x] Oferecer assistir/retomar e remover manualmente.
- [x] Exibir limite de itens e armazenamento ocupado.
- [x] Permitir qualidade padrão e alteração individual por vídeo na preparação.
- [x] Tratar lista vazia, falha de preparação, armazenamento insuficiente e fonte não compatível na interface.

## FASE 8 — RECUPERAÇÃO E ROBUSTEZ

- [x] Preservar `.part` e retomar via Range quando suportado pela fonte.
- [x] Reconciliar registros e arquivos físicos sem apagar parciais ou órfãos automaticamente.
- [x] Não apagar arquivos completos ainda não assistidos durante recuperação; itens READY/PAUSED são preservados.
- [x] Recuperação de itens COMPLETED/REMOVED tenta remover também parciais residuais antes de apagar o registro.
- [x] Tratar arquivo ausente, corrompido ou incompleto: READY/PAUSED/PLAYING sem arquivo íntegro viram INTERRUPTED e perdem a referência inválida; arquivos publicados corrompidos são reportados como órfãos e preservados para inspeção.
- [x] Evitar exclusões duplicadas e condições de corrida (mutex de exclusão e bloqueio por cacheId; validar sob concorrência na Fase 10).
- [x] Persistir estado antes de liberar arquivo após conclusão/remoção: COMPLETED ou REMOVED é gravado antes da exclusão física, permitindo retry seguro após encerramento inesperado.

## FASE 9 — DOCUMENTAÇÃO E INTEGRAÇÃO

- [x] Atualizar arquitetura, changelog, progresso e instruções de retomada para refletir a implementação desta etapa.
- [x] Documentar estados, diretórios, migrações e política de retenção em ARQUITETURA.md e neste roadmap.
- [ ] Integrar as alterações na `main` sem force push.
- [ ] Gerar checkpoints completos quando o ambiente permitir obter o arquivo-fonte integral.

## FASE 10 — VALIDAÇÃO FINAL (SOMENTE APÓS IMPLEMENTAÇÃO)

- [ ] Testes de isolamento entre bibliotecas, filas e históricos.
- [ ] Testes de migração Room.
- [ ] Testes de download/cancelamento/retomada para fontes autorizadas.
- [ ] Testes de reprodução offline.
- [ ] Testes de exclusão automática e manual.
- [ ] Testes de limite, armazenamento insuficiente e recuperação.
- [ ] Regressão de áudio, vídeo, tela cheia e segundo plano.
- [ ] Build debug/release e validação em dispositivo.
- [ ] Corrigir falhas e repetir testes afetados.

## Escopo explícito

- O cache não terá importação de vídeos locais, galeria ou arquivos escolhidos pelo usuário. O domínio atende exclusivamente conteúdos fornecidos por fontes remotas que autorizem expressamente o armazenamento temporário.
- Não criar fluxo de seleção/cópia de arquivos locais nem ampliar a entidade para acomodá-los.

## Estado atual

- Fases 0–3.1: auditoria, separação dos domínios, persistência, recuperação inicial e gerenciador implementados; validação funcional reservada à Fase 10.
- Fase 3.2: contrato de admissão de fontes, registro de provedores, downloader HTTP exclusivo de vídeo, progresso, cancelamento com preservação do parcial, retomada por Range, verificação de espaço e serviço de primeiro plano exclusivo de vídeo implementados.
- Fase 4: núcleo de transferência, publicação atômica, verificação/reserva concorrente de espaço conhecido, validação de contêiner/faixa de vídeo, serviço de primeiro plano e consulta das qualidades realmente oferecidas pelo provedor implementados. Falta registrar um provedor real cuja fonte autorize armazenamento local.
- Fase 5: reprodução de arquivos locais pelo serviço Media3 existente implementada; posição e estado são persistidos.
- Fase 6: regra temporária de retenção implementada no player e na interface. A conclusão natural exclui o arquivo; pausa, troca de tela, encerramento e seek manual até o fim preservam o cache.
- Fase 7: seção Cache na Central de Vídeos implementada com estado, qualidade, tamanho, limite, miniatura, duração, assistir/retomar, remoção manual, lista vazia, erros de fonte/espaço, seletor 240p/360p/480p, progresso de transferência e posição/progresso de visualização persistidos.
- Fase 8: recuperação trata arquivos ausentes/corrompidos sem apagá-los silenciosamente, preserva parciais/órfãos e reconcilia estados. Remoção manual persiste REMOVED antes da exclusão; conclusão natural persiste COMPLETED. Concorrência real permanece para a Fase 10.
- Fase 10: reservada exclusivamente à validação final. Nenhum teste ou build foi executado nesta etapa.
- Bloqueio conhecido: não há provedor oficialmente autorizado confirmado para cache temporário de vídeos do YouTube. O registro de provedores permanece vazio; a reprodução online continua intacta. Não contornar restrições da fonte.
- Regra de escopo: nenhum arquivo, classe, serviço, fila, histórico ou biblioteca da Central de Áudio foi alterado nesta etapa.

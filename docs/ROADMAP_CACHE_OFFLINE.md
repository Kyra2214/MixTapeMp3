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
6. Cache persistente nunca é apagado por fechar o app, trocar de aba, pausar ou interromper a reprodução.
7. Exclusão automática somente após conclusão real da visualização; exclusão manual sempre disponível.
8. Qualidade por vídeo: 240p (padrão), 360p e 480p. Não oferecer HD/Full HD.
9. Não remover cache existente automaticamente para abrir espaço. Limite atingido deve gerar aviso e ação explícita do usuário.
10. A preparação offline deve usar somente fontes e mecanismos que autorizem download e armazenamento local. Não implementar extração, captura, gravação ou cache persistente de streams do YouTube fora dos mecanismos oficiais autorizados. A implementação de cache para vídeos do YouTube depende de uma via oficialmente permitida; uso pessoal não elimina as restrições da fonte.

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

- [ ] Criar entidade exclusiva para itens de cache de vídeo.
- [ ] Criar DAO e repositório próprios, sem reutilizar `TrackEntity`.
- [ ] Criar migração Room explícita e schema exportado.
- [ ] Persistir identificador da fonte, título, miniatura, duração, qualidade, tamanho, caminho, estado, posição e datas.
- [ ] Modelar estados: aguardando, baixando, interrompido, pronto, em reprodução, pausado, concluído e removido.
- [ ] Garantir recuperação consistente após encerramento inesperado.
- [ ] Não marcar conteúdo remoto como offline disponível antes da confirmação de arquivo completo e íntegro.

## FASE 3 — GERENCIADOR DE CACHE

- [ ] Criar módulo de gerenciamento do cache de vídeo.
- [ ] Definir limite configurável: 1, 2, 3, 5, 10 ou personalizado.
- [ ] Impedir remoção automática de itens prontos para liberar vaga.
- [ ] Expor espaço ocupado e espaço livre.
- [ ] Suportar remoção manual.
- [ ] Recuperar estados e arquivos após reinicialização.
- [ ] Identificar arquivos órfãos sem apagar arquivos válidos não assistidos.
- [ ] Restringir provedores a fontes com autorização técnica e contratual para armazenamento offline.

## FASE 4 — PREPARAÇÃO OFFLINE E QUALIDADE

- [ ] Implementar adaptador de fonte para conteúdo que autorize download e armazenamento local.
- [ ] Seleção individual: 240p (padrão), 360p e 480p.
- [ ] Exibir somente qualidades realmente disponíveis.
- [ ] Download em segundo plano com progresso, cancelamento e recuperação quando suportada.
- [ ] Gravar em arquivo temporário e promover atomicamente após conclusão e validação.
- [ ] Verificar espaço disponível antes de iniciar.
- [ ] Não implementar downloader ou cache persistente de streams do YouTube por métodos não autorizados. A integração específica com YouTube permanece bloqueada até existir mecanismo oficialmente permitido para esse uso.

## FASE 5 — REPRODUÇÃO OFFLINE

- [ ] Priorizar arquivo local válido quando houver cache autorizado.
- [ ] Reproduzir sem internet quando o arquivo estiver completo.
- [ ] Persistir posição e estado durante a reprodução.
- [ ] Retomar vídeo interrompido sem perder o arquivo.
- [ ] Preservar tela cheia, controles, MediaSession e reprodução em segundo plano existentes.
- [ ] Não declarar conclusão por seek manual para o fim; exigir conclusão natural da reprodução.

## FASE 6 — REGRA DE EXCLUSÃO

- [ ] Excluir automaticamente somente após conclusão natural confirmada.
- [ ] Manter arquivo ao pausar, sair do vídeo, trocar de aba, fechar o app, reiniciar aparelho ou perder conexão.
- [ ] Permitir exclusão manual a qualquer momento.
- [ ] Remoção deve ser idempotente e sincronizar arquivo e banco.
- [ ] Nunca apagar cache não assistido durante limpeza de recuperação.

## FASE 7 — INTERFACE DA CENTRAL DE VÍDEOS

- [ ] Criar seção Cache Offline independente da biblioteca de áudio.
- [ ] Exibir miniatura, título, duração, qualidade, tamanho e estado.
- [ ] Exibir progresso do download e da visualização.
- [ ] Oferecer assistir/retomar e remover manualmente.
- [ ] Exibir limite de itens e armazenamento ocupado.
- [ ] Permitir qualidade padrão e alteração individual por vídeo.
- [ ] Tratar lista vazia, falha, armazenamento insuficiente e fonte não compatível.

## FASE 8 — RECUPERAÇÃO E ROBUSTEZ

- [ ] Recuperar downloads interrompidos quando suportado pela fonte.
- [ ] Reconciliar registros e arquivos físicos.
- [ ] Não apagar arquivos completos ainda não assistidos.
- [ ] Tratar arquivo ausente, corrompido ou incompleto.
- [ ] Evitar exclusões duplicadas e condições de corrida.
- [ ] Persistir estado antes de liberar arquivo após conclusão.

## FASE 9 — DOCUMENTAÇÃO E INTEGRAÇÃO

- [ ] Atualizar arquitetura, changelog, progresso e instruções de retomada.
- [ ] Documentar estados, diretórios, migrações e política de retenção.
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

## Estado atual

- Fase 0: concluída por inspeção estática.
- Fase 1.1: implementada no código e documentada; validação funcional transferida para a fase final.
- Fase 1.2 (fronteiras de armazenamento): implementada; o cache ainda não cria arquivos até a fase do gerenciador.
- Fases 2–9: pendentes.
- Fase 10: reservada exclusivamente à validação final.
- Percentual de implementação deste roadmap: Fase 1.1 concluída; fases seguintes pendentes. Validação final ainda não executada.
- Bloqueio conhecido: não há via autorizada confirmada para baixar e armazenar permanentemente vídeos do YouTube fora dos recursos oficiais. Não contornar essa restrição.

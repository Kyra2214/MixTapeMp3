# RESUME_SESSION — Central de Vídeos e Cache Offline

- Projeto: `Kyra2214/MixTapeMp3`
- Branch: `main`
- Último commit: aguarda publicação do checkpoint de fronteiras de armazenamento.
- Fase atual: Fase 2 — modelo e persistência do cache.
- Fase 0: auditoria concluída.
- Fase 1.1: implementada; prévias iniciadas na Central de Áudio não gravam histórico/progresso na Central de Vídeos.
- Fase 1.2: implementada; `MediaStorageLayout` separa `filesDir/imports` (áudio, caminho legado preservado) e `filesDir/video_cache` (cache de vídeo); importador e recuperação usam somente o diretório de áudio.
- Próximo submódulo: Fase 2.1 — modelo Room exclusivo para itens de cache de vídeo, com migração explícita.
- Testes/build: não executar até completar todo o roadmap; validação reservada à Fase 10.
- Bloqueio conhecido: cache persistente de vídeos do YouTube requer mecanismo oficialmente permitido. Não contornar restrições da fonte.
- Último ZIP completo: não gerado; clone/arquivo remoto não está acessível pelo ambiente atual.

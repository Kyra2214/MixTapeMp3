# CHECKLIST DE VALIDAÇÃO MANUAL (T-702)
Marque cada item no aparelho. Nada foi compilado nem executado no ambiente de desenvolvimento.

## 1. Compilação
- [ ] `./gradlew assembleDebug` roda de um clone limpo (wrapper com .jar presente, JDK 17).
- [ ] Dependência LAME resolve; `./gradlew :app:dependencies` sem erro (T-101).
- [ ] AGP/Gradle aceitam compileSdk 36; se não, atualizar conforme docs/BUILD.md (T-602).
- [ ] `app/schemas/.../10.json` gerado e versionado (T-103).
- [ ] `.so` de arm64-v8a no APK e alinhado a 16 KB (`unzip -l` / `zipalign -c -P 16 4`).

## 2. MP3 gerado
- [ ] CBR 320 kbps, 44,1 kHz, duração igual à soma das faixas (Mp3Validator libera o arquivo).
- [ ] Dois CDs com o mesmo nome geram dois arquivos; regerar o mesmo CD substitui o seu (T-401).
- [ ] Tags ID3 (título, álbum, artista, capa), capítulos CHAP e CUE corretos em players externos (um com e outro sem suporte a capítulos).

## 3. Reprodução
- [ ] Notificação com controles; primeiro play no Android 13+ pede permissão e toca mesmo se negada (T-503).
- [ ] Bluetooth conecta/desconecta sem travar; ligação durante a reprodução pausa e retomar pede foco de novo (T-505).
- [ ] Tocar MP3 grande não congela a tela (T-504).

## 4. Geração em segundo plano
- [ ] Serviço em primeiro plano com notificação; tela apagada não interrompe; wake lock liberado ao terminar.
- [ ] CD muito longo (várias horas): geração conclui ou é cancelada sem arquivo parcial no limite do serviço dataSync (T-602).
- [ ] Cancelar não deixa .part/.tagged; matar o app durante a geração e reabrir mostra "interrompido".

## 5. Armazenamento
- [ ] SAF: importar lote grande; falha de importação não deixa permissão presa; excluir faixa libera a permissão (T-201).
- [ ] Excluir faixa baixada por link apaga o arquivo em imports; originais nunca são tocados (T-202).
- [ ] Pasta de destino escolhida na Home e em Exportar produz o mesmo estado (T-203).
- [ ] FileProvider: compartilhar e "Abrir em outro player" funcionam.
- [ ] Destino FAT32: aviso para CD acima de 4 GB.
- [ ] Reabrir o app mantém "excluir cópias" e o CUE aponta para o nome real do MP3 (T-402).

## 6. Telas
- [ ] Editor de CD com 100 faixas e Player com muitos capítulos/MP3s: tudo rola e os botões são alcançáveis; rotação preserva o estado (T-501/T-502).
- [ ] Edge-to-edge no Android 15/16: nada coberto por barras do sistema.
- [ ] Excluir CD pede confirmação e não apaga músicas nem MP3 gerados (T-205).
- [ ] Equalizador: EQ automática e "Zerar EQ" só afetam o CD escolhido (T-303).
- [ ] Arquivo corrompido mostra "Falha na análise" com "Tentar de novo" (T-301).

## 7. Offline
- [ ] Modo avião: tudo funciona, exceto o importador por link, que avisa com clareza.

## Importação por link do YouTube
- [ ] Link `watch?v=`, `youtu.be/`, `/shorts/` e `/live/` reconhecido; playlist e canal recusados com mensagem clara.
- [ ] Lista de faixas de áudio aparece ordenada por bitrate, com tamanho.
- [ ] Download conclui, a música entra na biblioteca e o arquivo tem a extensão correta (`m4a`/`webm`).
- [ ] Cancelar no meio não deixa `.part` nem faixa quebrada; reabrir o app limpa resíduo.
- [ ] Excluir a faixa apaga o arquivo de `filesDir/imports`.
- [ ] Vídeo privado, com restrição de idade e de região devolvem a mensagem certa.
- [ ] Modo avião: aviso imediato, sem esperar timeout.
- [ ] Faixa importada gera CD em 320 kbps CBR normalmente (o `MediaCodec` decodifica opus/aac).

## Integração (Configurações, gêneros, cópia automática)
- [ ] Atualizar da v10 para a v11 mantém a biblioteca; músicas antigas ganham gênero ao abrir o app.
- [ ] Biblioteca: aba Músicas agrupa por gênero, "Gênero" edita, busca acha por gênero; aba Playlists lista as geradas e abre no ponto da música tocada.
- [ ] Gerar playlist com pasta escolhida: MP3 (e CUE, se ligado) aparece na pasta; regerar substitui a cópia.
- [ ] Gerar com "Apagar os originais": só apaga depois do MP3 pronto/copiado; falha na cópia mantém os originais.
- [ ] "← Voltar" funciona em todas as telas internas; navegador, Downloads (fila de até 5, retomar/reiniciar) e notificação do serviço continuam como antes.
- [ ] Player: volume do aparelho e do player; botões físicos controlam a mídia.


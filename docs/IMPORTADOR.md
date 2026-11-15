# IMPORTADOR UNIVERSAL — política de fontes
- Link direto (arquivo de áudio via HTTPS): suportado. Baixado como original, sem reconversão.
- 4shared: apenas links diretos de arquivo que o usuário tem permissão de baixar (o conector direto é usado). Páginas HTML do serviço não são analisadas/rastreadas. Integração via API oficial exigiria credenciais/OAuth do desenvolvedor — fora do escopo offline-first atual.
- YouTube, Facebook, Instagram: reconhecidos, mas NÃO baixados. Não há meio oficial de download de mídia para apps de terceiros e os termos das plataformas proíbem a extração. O app orienta o usuário a importar um arquivo obtido com autorização via "Importar músicas" (Biblioteca).
- ASSUMINDO: HTTPS apenas (cleartext bloqueado pelo Android); importação em primeiro plano até a Fase 06 (WorkManager).

# Plano de implantação — fase 5

## Objetivo e escopo

Preparar o LedgerLens para deploy do frontend estático na Vercel e do backend Spring Boot em um serviço de contêiner, documentando Render como caminho opcional, PostgreSQL gerenciado e alternativas Railway/Fly.io. Endurecer configuração de publicação, automatizar verificações e revisar vulnerabilidades das dependências de produção. Não alterar código de aplicação nem migrar serviços Azure.

## Descobertas da análise

- Monorepo com backend Java/Spring Boot/Maven em `backend/` e frontend React/Vite/npm em `frontend/`.
- `backend/pom.xml` define `<java.version>25</java.version>` e Spring Boot `3.5.0`; o pedido menciona JRE 21, portanto há divergência real. O plano preserva o alvo declarado no POM e usará Java/JRE 25 nos artefatos para não fazer downgrade/alteração de runtime sem autorização. A CI seguirá o mesmo alvo. Verificar a compatibilidade do par Spring Boot 3.5.0/Java 25 por build e registrar a limitação se necessário.
- Backend já lê `PORT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` e `ALLOWED_ORIGINS` em produção, expõe apenas `/actuator/health`, e usa Flyway/PostgreSQL. O datasource local possui credenciais de desenvolvimento no YAML.
- Frontend lê `VITE_API_URL`; no desenvolvimento Vite encaminha `/api` para `localhost:8080`. `frontend/package-lock.json` existe; testes são via Vitest.
- Importação CSV aceita valores monetários não negativos; conciliação prioriza referência+valor, depois data+valor, depois candidatos por similaridade com tolerância configurável; o matcher usa diferença ERP menos banco. Armazenamento é isolado por UUID de workspace e a retenção padrão é 24 horas.

## Artefatos previstos

1. `backend/Dockerfile`: build multi-stage usando Maven Wrapper e JDK 25, runtime JRE 25, usuário não-root, perfil `prod`, limite de memória JVM compatível com cgroups e healthcheck HTTP em `/actuator/health`.
2. `.dockerignore`: excluir VCS, artefatos de build, dependências e arquivos de ambiente/segredos do contexto.
3. `render.yaml`: blueprint opcional para serviço Docker do backend, healthcheck, variáveis necessárias sem valores secretos e conexão externa a PostgreSQL gerenciado (sem provisionar banco pago automaticamente).
4. `frontend/vercel.json`: build a partir da raiz `frontend`, rewrites SPA com fallback para `index.html` sem substituir arquivos estáticos, CSP contendo placeholder explícito do domínio de API e cabeçalhos de segurança (HSTS, nosniff, referrer, permissions e frame protection).
5. `frontend/.env.example`: documentar `VITE_API_URL` com placeholder do endpoint do backend; nunca incluir credenciais.
6. `.github/workflows/verify.yml`: CI em push/PR com Java 25, testes/verificação Maven e Node compatível com Vite 8, `npm ci`, testes e build.
7. `.github/dependabot.yml`: atualizações semanais Maven, npm e GitHub Actions.
8. `README.md`: apresentação em pt-BR, placeholders de demo/imagens, Mermaid, instruções local/Windows/Maven, regras e convenção de sinais, deploy, env vars, privacidade/retenção e decisões técnicas.
9. `backend/pom.xml`: ajustar versões gerenciadas somente para vulnerabilidades críticas/altas confirmadas com patch release compatível, evitando upgrades sem justificativa.
10. `.github/modernize/deployment/summary.md`: resumo final com verificações, vulnerabilidades/limitações e arquivos atualizados.

Não modificar fontes Java/JS, dependências, infraestrutura alheia ao escopo ou arquivos de terceiros sem necessidade comprovada.

## Etapas e validação obrigatória

1. Registrar plano/progresso; preservar alterações existentes. Na invocação standalone, preparar branch via ferramenta de versionamento, sem comandos git diretos.
2. Ler este plano e o progresso antes de implementar.
3. Gerar os artefatos listados; revisar que a CSP contém placeholder intencional e instrução inequívoca para substituí-lo antes do deploy, e que nenhuma senha/token foi adicionado.
4. Avaliar dependências de produção sem transmitir código ou segredos: `npm audit --omit=dev` com o lockfile e relatório Maven de vulnerabilidades (OWASP Dependency-Check, sem enviar artefatos do projeto); se a fonte de CVEs limitar o relatório, registrar e usar scan do artefato compilado mais árvore Maven para confirmação. Corrigir vulnerabilidades críticas (e altas com patch compatível e direto) e triá-las contra os advisories upstream; não fazer upgrades preventivos/desnecessários.
5. Verificar JSON/YAML, `npm ci`, testes e build frontend; Maven test/verify e relatório de dependências quando o ambiente permitir; build Docker e inspeção/scan de imagem se Docker e ferramentas permitirem. Não executar serviços com dados reais.
6. Atualizar progresso e resumo, registrar resultados exatos e limitações, validar alterações e fazer commit final exclusivamente pela ferramenta de versionamento, se todas as tarefas aplicáveis terminarem.

## Critérios de aceite

- Artefatos da lista presentes e coerentes com o runtime declarado em `pom.xml`.
- Nenhum segredo gravado; env vars de produção documentadas, com exemplos não sensíveis.
- CI executa testes/build do backend e frontend.
- Rewrites não prejudicam a entrega normal de assets estáticos.
- Vulnerabilidades de produção analisadas e resultados/limitações documentados.
- Execuções de validação registradas em `progress.md` e `summary.md`.

# Resumo da implantação — fase 5

## Entregas

- Container do backend em `backend/Dockerfile`: build multi-stage com Maven 3.9.16/JDK 21 e runtime `eclipse-temurin:21-jre-alpine`, usuário 10001 não-root, perfil `prod`, JVM limitada a 75% do limite de memória cgroup (com margem para memória nativa) e healthcheck `/actuator/health`.
- `.dockerignore` evita enviar `target`, `node_modules`, `.env`, chaves, VCS e artefatos não necessários ao build.
- `render.yaml` oferece um serviço Docker opcional com healthcheck e variáveis de banco/CORS sem valores secretos; o PostgreSQL precisa ser criado/selecionado separadamente.
- `frontend/vercel.json` configura root `frontend` (build, saída `dist`, rewrite SPA e headers CSP/HSTS/nosniff/frame/referrer/permissions). CSP usa `https://SEU-BACKEND.onrender.com` como placeholder em `connect-src`, a ser substituído pelo host real antes do deploy. `frontend/.env.example` documenta `VITE_API_URL` sem credenciais.
- `.github/workflows/verify.yml` verifica backend em Java 21 e frontend Node 22 (`npm ci`, testes e build). `.github/dependabot.yml` acompanha Maven, npm e GitHub Actions semanalmente.
- README completo em pt-BR com arquitetura Mermaid, placeholders para demo/capturas, execução local e Windows, endpoints, regras/convenção de sinais, configuração do ambiente, deploy Vercel/Render/Railway/Fly.io, banco gerenciado, privacidade/retenção e decisões técnicas.

## Decisão sobre Java

O pedido especificou JRE 21. POM, Docker e CI foram alinhados a Java 21. A compilação local usou JDK 25 com `--release 21`; o Docker foi construído com builder JDK 21 e a imagem executou Temurin 21.0.12.1. A CI instala Java 21.

## Ajustes identificados durante a validação

- O índice único de idempotência anterior era global; isso fazia uploads idênticos em workspaces diferentes colidirem. A migration V5 agora escopa o índice por `workspace_id`, mantendo a idempotência dentro de cada workspace.
- O teste de retenção passava `Instant` diretamente ao `JdbcTemplate`, sem tipo SQL inferível no PostgreSQL. O teste usa `Timestamp.from` e a suite de integração passou.
- O estágio de build não executa mais `dependency:go-offline`: o goal tentava resolver artefatos opcionais via um repositório privado Flyway e falhava sem credenciais. O empacotamento Maven normal no builder Java 21 resolve as dependências necessárias e conclui.

## Auditoria de dependências

- `npm audit --omit=dev`: **0 vulnerabilidades** em dependências frontend de produção.
- A primeira imagem foi analisada pelo Docker Scout e apresentou 9 advisories classificados como Critical: 7 em `tomcat-embed-core` 10.1.41 e 2 associados a `spring-webmvc` 6.2.7. O POM foi atualizado, dentro da linha Spring Boot 3.5, para 3.5.16 com Tomcat 10.1.61. Também foram aplicados patches compatíveis sugeridos pelo scan: Jackson 2.21.7, PostgreSQL JDBC 42.7.12 e Log4j 2.25.5.
- Docker Scout na imagem final Java 21 registrou **6 findings em 3 pacotes** — `2 Critical`, `0 High`, `2 Medium`, `1 Low`, `1 Unspecified`. Não há findings reportados para Tomcat, Jackson, PostgreSQL JDBC ou Log4j.
- Os dois findings Critical restantes são de Spring Framework 6.2.19 (CVE-2026-47890 e CVE-2026-47884). Docker Scout atribui CRITICAL/CVSS 9.8, mas os advisories oficiais Spring classificam-nos como **LOW** e **MEDIUM**, respectivamente. As condições descritas são uso de SSE com view fragments controláveis por atacante e uso de `XsltView` com mapping `/**` sem view explícita; a implementação LedgerLens é REST e não usa esses fluxos. O framework 6.2.20 está listado no advisory como correção Enterprise, e migrar para Spring Boot 4 seria uma alteração major sem necessidade demonstrada para esta aplicação. A discrepância de severidade e a ausência de pré-condições foram registradas, sem upgrade major preventivo.
- Os demais findings são pacotes da imagem Alpine: `coreutils` 9.11-r0 (2 Medium, 1 Low; feed não informa versão corrigida) e `musl` 1.2.6-r3 (1 Unspecified; o feed aponta 1.2.6-r6 como correção). Não pertencem às dependências Maven/npm; acompanhar atualizações do runtime base.
- OWASP Dependency-Check/Maven foi tentado com escopo runtime/test excluído, mas não produziu relatório: sem chave NVD, a atualização de 413.111 entradas ficou presa em retries após 10.000 (2%) e falhas para `startIndex=10000/12000`. O processo foi cancelado sem gerar relatório confiável. Como análise complementar, `mvn dependency:tree -Dscope=runtime` concluiu e confirmou os níveis de dependência resolvidos; Docker Scout examinou a SBOM da imagem final. Nenhum código ou segredo foi enviado.

## Verificações

| Verificação | Resultado |
|---|---|
| `npm ci` | Sucesso; 114 pacotes adicionados, auditoria do npm reportou 0 vulnerabilidades. |
| `npm test` | Sucesso: 4 arquivos, 13 testes aprovados. |
| `npm run build` | Sucesso; Vite gerou `frontend/dist`. |
| `npm audit --omit=dev` | Sucesso: 0 vulnerabilidades. |
| Maven `verify` com `DefaultCsvParserServiceTest,ReconciliationMatcherTest` após `java.version=21` | BUILD SUCCESS: 15 testes, 0 failures/errors/skipped; JAR empacotado. Host JDK 25, compilação direcionada ao release 21. No Windows, o wrapper foi executado com `MAVEN_USER_HOME=C:\m2cache` por causa do espaço no caminho padrão do perfil. |
| Maven `clean verify` com release 21 | BUILD SUCCESS: 29 testes, 0 failures/errors/skipped; Testcontainers validou upload, isolamento, retenção e schema. Host JDK 25 com `--release 21`. |
| Build final da imagem Docker | Sucesso: `ledgerlens-backend:phase5-java21`; builder JDK 21, runtime Temurin 21.0.12.1, usuário UID/GID 10001 e healthcheck. |
| Docker Scout na imagem Java 21 | 6 findings: 2 Critical Spring (triados como severidade/aplicabilidade excessivas diante das pré-condições ausentes), 0 High, 2 Medium, 1 Low e 1 Unspecified em pacotes Alpine. |
| JSON/YAML | `frontend/vercel.json` parseado como JSON; `render.yaml`, `.github/dependabot.yml` e workflow parseados como YAML válido. |
| Regressão após ajustes Java/CSP | `npm test`: 13 aprovados; `npm run build`: sucesso sem warnings. Maven release 21: 29 testes aprovados. |

## Arquivos modificados/criados

- `.dockerignore`
- `.github/dependabot.yml`
- `.github/workflows/verify.yml`
- `.github/modernize/deployment/plan.md`
- `.github/modernize/deployment/progress.md`
- `.github/modernize/deployment/summary.md`
- `README.md`
- `backend/Dockerfile`
- `backend/pom.xml`
- `backend/src/main/resources/db/migration/V5__scope_import_idempotency_to_workspace.sql`
- `backend/src/test/java/com/ledgerlens/WorkspaceSecurityIntegrationTest.java`
- `frontend/.env.example`
- `frontend/vercel.json`
- `render.yaml`

**Controle de versão:** commit inicial `9848f3c0587f0d8927a840f3853e90514f33fb27` na branch `modernize/java-20261022142948`. As correções finais Java 21/CSP, migration V5, Dockerfile e atualizações de documentação/testes ainda estão no working tree e não foram commitadas.

## Configuração necessária antes do deploy

1. Substituir `https://SEU-BACKEND.onrender.com` no CSP de `frontend/vercel.json`.
2. Definir `VITE_API_URL` no build Vercel e `ALLOWED_ORIGINS` no backend usando a origem Vercel real.
3. Inserir `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD` como secrets/runtime vars no provedor.
4. Usar PostgreSQL gerenciado com TLS/backups e configurar um limite de memória compatível com as necessidades do workload.
5. Preencher URLs e capturas placeholder no README com conteúdo de demonstração sem dados financeiros reais.

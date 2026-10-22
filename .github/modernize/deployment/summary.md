# Resumo da implantação — fase 5

## Entregas

- Container do backend em `backend/Dockerfile`: build multi-stage com Maven 3.9.16/JDK 25 e runtime `eclipse-temurin:25-jre-alpine`, usuário 10001 não-root, perfil `prod`, JVM limitada a 75% do limite de memória cgroup (com margem para memória nativa) e healthcheck `/actuator/health`.
- `.dockerignore` evita enviar `target`, `node_modules`, `.env`, chaves, VCS e artefatos não necessários ao build.
- `render.yaml` oferece um serviço Docker opcional com healthcheck e variáveis de banco/CORS sem valores secretos; o PostgreSQL precisa ser criado/selecionado separadamente.
- `frontend/vercel.json` configura root `frontend` (build, saída `dist`, rewrite SPA e headers CSP/HSTS/nosniff/frame/referrer/permissions). CSP tem o placeholder explícito `https://REPLACE-WITH-BACKEND-HOST.example.com`, que deve ser substituído pelo host real antes do deploy. `frontend/.env.example` documenta `VITE_API_URL` sem credenciais.
- `.github/workflows/verify.yml` verifica backend em Java 25 e frontend Node 22 (`npm ci`, testes e build). `.github/dependabot.yml` acompanha Maven, npm e GitHub Actions semanalmente.
- README completo em pt-BR com arquitetura Mermaid, placeholders para demo/capturas, execução local e Windows, endpoints, regras/convenção de sinais, configuração do ambiente, deploy Vercel/Render/Railway/Fly.io, banco gerenciado, privacidade/retenção e decisões técnicas.

## Decisão sobre Java

O pedido mencionava JRE 21, mas `backend/pom.xml` original define Java 25. Nenhuma migração de runtime foi feita: Docker e CI usam Java 25 para permanecer alinhados ao manifesto. O contêiner verificado executa Temurin 25.0.4.1. A aplicação compilou/testou com essa versão. Se Java 21 for requisito, será necessária uma alteração deliberada do POM/runtime em outra tarefa.

## Auditoria de dependências

- `npm audit --omit=dev`: **0 vulnerabilidades** em dependências frontend de produção.
- A primeira imagem foi analisada pelo Docker Scout e apresentou 9 advisories classificados como Critical: 7 em `tomcat-embed-core` 10.1.41 e 2 associados a `spring-webmvc` 6.2.7. O POM foi atualizado, dentro da linha Spring Boot 3.5, para 3.5.16 com Tomcat 10.1.61. Também foram aplicados patches compatíveis sugeridos pelo scan: Jackson 2.21.7, PostgreSQL JDBC 42.7.12 e Log4j 2.25.5.
- Scan completo da imagem final: **6 findings em 3 pacotes** — `2 Critical`, `0 High`, `2 Medium`, `1 Low`, `1 Unspecified`. Não há mais findings para Tomcat, Jackson, PostgreSQL JDBC ou Log4j.
- Os dois findings Critical restantes são de Spring Framework 6.2.19 (CVE-2026-47890 e CVE-2026-47884). Docker Scout atribui CRITICAL/CVSS 9.8, mas os advisories oficiais Spring classificam-nos como **LOW** e **MEDIUM**, respectivamente. As condições descritas são uso de SSE com view fragments controláveis por atacante e uso de `XsltView` com mapping `/**` sem view explícita; a implementação LedgerLens é REST e não usa esses fluxos. O framework 6.2.20 está listado no advisory como correção Enterprise, e migrar para Spring Boot 4 seria uma alteração major sem necessidade demonstrada para esta aplicação. A discrepância de severidade e a ausência de pré-condições foram registradas, sem upgrade major preventivo.
- Os demais findings são pacotes da imagem Alpine: `coreutils` (2 Medium, 1 Low; feed não informa versão corrigida) e `musl` (1 Unspecified, o feed aponta 1.2.6-r6). Não pertencem às dependências Maven/npm; acompanhar atualizações do runtime base.
- OWASP Dependency-Check/Maven foi tentado com escopo runtime/test excluído, mas não produziu relatório: sem chave NVD, a atualização de 413.111 entradas ficou presa em retries após 10.000 (2%) e falhas para `startIndex=10000/12000`. O processo foi cancelado sem gerar relatório confiável. Como análise complementar, `mvn dependency:tree -Dscope=runtime` concluiu e confirmou os níveis de dependência resolvidos; Docker Scout examinou a SBOM da imagem final. Nenhum código ou segredo foi enviado.

## Verificações

| Verificação | Resultado |
|---|---|
| `npm ci` | Sucesso; 114 pacotes adicionados, auditoria do npm reportou 0 vulnerabilidades. |
| `npm test` | Sucesso: 4 arquivos, 13 testes aprovados. |
| `npm run build` | Sucesso; Vite gerou `frontend/dist`. |
| `npm audit --omit=dev` | Sucesso: 0 vulnerabilidades. |
| Maven `verify` com `DefaultCsvParserServiceTest,ReconciliationMatcherTest` | BUILD SUCCESS: 15 testes, 0 failures/errors/skipped; JAR empacotado. |
| Maven `verify` sem filtro | Limitado: 29 testes, 14 errors de Testcontainers por Docker Desktop/API retornar HTTP 400 e ambiente Docker inválido; testes de integração não validados neste host. |
| Build de imagem Docker | Sucesso: `ledgerlens-backend:phase5`; tamanho reportado pelo Scout 127 MB, 128 pacotes indexados. |
| Inspeção da imagem | JRE Temurin 25.0.4.1; execução UID/GID 10001; healthcheck Docker configurado. |
| Docker Scout (imagem final) | 6 findings: 2 Critical (triados como falso excesso de severidade/aplicabilidade Spring), 0 High, 2 Medium, 1 Low e 1 Unspecified. |
| JSON/YAML | `frontend/vercel.json` parseado como JSON; `render.yaml`, `.github/dependabot.yml` e workflow parseados como YAML válido. |

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
- `frontend/.env.example`
- `frontend/vercel.json`
- `render.yaml`

**Controle de versão:** a ferramenta de versionamento informa que exclui os três arquivos de acompanhamento `.github/modernize/deployment/{plan,progress,summary}.md` como tooling; eles permanecem no workspace e podem não integrar o commit, conforme sua política. Os demais arquivos da lista são alterações versionáveis.

## Configuração necessária antes do deploy

1. Substituir o host placeholder do CSP em `frontend/vercel.json`.
2. Definir `VITE_API_URL` no build Vercel e `ALLOWED_ORIGINS` no backend usando a origem Vercel real.
3. Inserir `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD` como secrets/runtime vars no provedor.
4. Usar PostgreSQL gerenciado com TLS/backups e configurar um limite de memória compatível com as necessidades do workload.
5. Preencher URLs e capturas placeholder no README com conteúdo de demonstração sem dados financeiros reais.

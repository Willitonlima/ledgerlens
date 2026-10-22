# Progresso — fase 5 de deploy

## Geral

- Objetivo: preparar documentação e artefatos de deploy do LedgerLens (frontend Vercel, backend contêiner, Render opcional).
- Branch: `modernize/java-20261022142948` (anterior `master`), criada via ferramenta; não foi necessário descartar alterações.
- Análise: concluída — backend Java/Spring Boot/Maven + frontend React/Vite/npm.
- Divergência identificada: POM original declarava Java 25, enquanto o pedido menciona JRE 21. POM, Dockerfile e CI alinhados a Java 21; Spring Boot atualizado de 3.5.0 para 3.5.16 como correção de segurança patch-level.

## Fases

| Fase | Estado | Observações |
|---|---|---|
| Geração do plano | concluída | `.github/modernize/deployment/plan.md`; analisados manifests e configuração real. |
| Controle de versão | concluída | Branch `modernize/java-20261022142948`; base `master`. |
| Artefatos de deploy | concluída | Docker/JRE 21, Render opcional, Vercel, CI Java 21, Dependabot, README e atualização pontual de dependências por advisories críticos do scan. |
| Auditoria de dependências | concluída com limitação | `npm audit --omit=dev`: 0 vulnerabilidades; Maven Dependency-Check bloqueado/retardado pela NVD sem API key; árvore Maven e Docker Scout usados para avaliar o artefato. |
| Verificações | concluída | Frontend, `mvn clean verify` (29 testes), Docker build Java 21 e configuração do container validados. O relatório OWASP Maven segue limitado pela fonte NVD sem API key. |
| Resumo e commit | commit inicial concluído; correções posteriores não commitadas | Commit inicial `9848f3c0587f0d8927a840f3853e90514f33fb27`; correções Java 21/CSP, migration V5 e ajustes dos testes estão no working tree para revisão. |

## Auditoria/triagem

- Docker Scout inicialmente encontrou 7 advisories críticos no `tomcat-embed-core` 10.1.41 e 2 itens para `spring-webmvc` 6.2.7. Tomcat foi atualizado para 10.1.61 via gerenciamento de dependências do Spring Boot 3.5.16; Jackson 2.21.7, PostgreSQL JDBC 42.7.12 e Log4j 2.25.5 foram fixados pelos patches indicados no scan para findings altos/médios.
- O scan ainda classifica 2 CVEs Spring como CRITICAL em Spring MVC 6.2.19. Os advisories upstream do Spring classificam CVE-2026-47890 como LOW e CVE-2026-47884 como MEDIUM, com pré-condições SSE/view fragment e XsltView/mapping `/**`, respectivamente; a implementação REST do LedgerLens não usa esses fluxos. Registrar discrepância e não fazer migração major sem necessidade.
- OWASP Dependency-Check iniciou a atualização dos 413.111 registros NVD sem API key, baixou 10.000 (2%) e ficou em repetidas falhas/retries para `startIndex=10000`/`12000`; processo cancelado após não progredir. Não gerou relatório confiável.
- `mvn dependency:tree -Dscope=runtime` concluiu e confirmou `tomcat-embed-core` 10.1.61 e `spring-webmvc` 6.2.19.
- Docker Scout na imagem final Java 21 encontrou 6 findings em 3 pacotes (2 Critical, 0 High, 2 Medium, 1 Low, 1 Unspecified). Os 2 findings Spring são classificados como LOW/MEDIUM nos advisories upstream e dependem de SSE/XsltView, fluxos ausentes no app; coreutils não tem correção indicada no feed, e musl aponta 1.2.6-r6 como versão corrigida.

## Verificações concluídas

- `npm audit --omit=dev`: 0 vulnerabilidades; `npm ci`: sucesso; `npm test`: 4 arquivos/13 testes aprovados; `npm run build`: sucesso.
- Após alinhar o POM para Java 21, `MAVEN_USER_HOME=C:\m2cache .\mvnw.cmd -B -ntp clean verify`: BUILD SUCCESS, 29 testes aprovados; JDK host 25, compilação direcionada ao release 21, incluindo integração/Testcontainers.
- `docker build --tag ledgerlens-backend:phase5-java21 --file backend/Dockerfile .`: sucesso com builder JDK 21 e runtime Temurin 21.0.12.1, UID/GID 10001 e healthcheck presente. `dependency:go-offline` foi removido porque tentava baixar artefatos opcionais de um repositório Flyway privado; o build do pacote funciona sem essa etapa.
- O build completo revelou conflito do índice global de idempotência entre workspaces e falha no binding de `Instant` do teste de retenção. A migration V5 agora inclui `workspace_id` no índice único e o teste usa `Timestamp`.
- `WorkspaceSecurityIntegrationTest`: 6 testes aprovados, inclusive isolamento por workspace e limpeza agendada.
- Após ajustar a CSP: `npm test` aprovou 13 testes e `npm run build` concluiu sem warnings.
- Configuração `frontend/vercel.json` validada como JSON; `render.yaml`, Dependabot e workflow validados como YAML.

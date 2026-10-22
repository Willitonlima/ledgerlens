# Progresso — fase 5 de deploy

## Geral

- Objetivo: preparar documentação e artefatos de deploy do LedgerLens (frontend Vercel, backend contêiner, Render opcional).
- Branch: `modernize/java-20261022142948` (anterior `master`), criada via ferramenta; não foi necessário descartar alterações.
- Análise: concluída — backend Java/Spring Boot/Maven + frontend React/Vite/npm.
- Divergência identificada: POM original declara Java 25, enquanto o pedido menciona JRE 21. Runtime mantido em Java 25; Spring Boot atualizado de 3.5.0 para 3.5.16 como correção de segurança patch-level.

## Fases

| Fase | Estado | Observações |
|---|---|---|
| Geração do plano | concluída | `.github/modernize/deployment/plan.md`; analisados manifests e configuração real. |
| Controle de versão | concluída | Branch `modernize/java-20261022142948`; base `master`. |
| Artefatos de deploy | concluída | Docker/JRE 25, Render opcional, Vercel, CI, Dependabot, README e atualização pontual de dependências por advisories críticos do scan. |
| Auditoria de dependências | concluída com limitação | `npm audit --omit=dev`: 0 vulnerabilidades; Maven Dependency-Check bloqueado/retardado pela NVD sem API key; árvore Maven e Docker Scout usados para avaliar o artefato. |
| Verificações | concluída com limitações | Frontend, testes unitários, pacote Maven, Docker build/config e scan realizados; suite de integração local depende de Docker API funcional. |
| Resumo e commit | resumo concluído | `summary.md` registra resultados e limitações; commit pendente. A ferramenta sinalizou que exclui os 3 arquivos `.github/modernize/deployment/*` como tooling; eles permanecem no workspace. |

## Auditoria/triagem

- Docker Scout inicialmente encontrou 7 advisories críticos no `tomcat-embed-core` 10.1.41 e 2 itens para `spring-webmvc` 6.2.7. Tomcat foi atualizado para 10.1.61 via gerenciamento de dependências do Spring Boot 3.5.16; Jackson 2.21.7, PostgreSQL JDBC 42.7.12 e Log4j 2.25.5 foram fixados pelos patches indicados no scan para findings altos/médios.
- O scan ainda classifica 2 CVEs Spring como CRITICAL em Spring MVC 6.2.19. Os advisories upstream do Spring classificam CVE-2026-47890 como LOW e CVE-2026-47884 como MEDIUM, com pré-condições SSE/view fragment e XsltView/mapping `/**`, respectivamente; a implementação REST do LedgerLens não usa esses fluxos. Registrar discrepância e não fazer migração major sem necessidade.
- OWASP Dependency-Check iniciou a atualização dos 413.111 registros NVD sem API key, baixou 10.000 (2%) e ficou em repetidas falhas/retries para `startIndex=10000`/`12000`; processo cancelado após não progredir. Não gerou relatório confiável.
- `mvn dependency:tree -Dscope=runtime` concluiu e confirmou `tomcat-embed-core` 10.1.61 e `spring-webmvc` 6.2.19.
- Scan final completo do Docker Scout: 6 findings em 3 pacotes (2 Critical, 0 High, 2 Medium, 1 Low, 1 Unspecified). Os 2 Critical atribuídos a Spring são classificados oficialmente como LOW/MEDIUM pelo projeto Spring e dependem de fluxos ausentes no LedgerLens; os demais findings são coreutils/musl do Alpine (sem correção do fornecedor indicada para coreutils no feed). Jackson/PostgreSQL/Log4j não aparecem mais no relatório.

## Verificações concluídas

- `npm audit --omit=dev`: 0 vulnerabilidades; `npm ci`: sucesso; `npm test`: 4 arquivos/13 testes aprovados; `npm run build`: sucesso.
- Maven `verify` com `-Dtest=DefaultCsvParserServiceTest,ReconciliationMatcherTest`: BUILD SUCCESS; 15 testes aprovados, 0 failures/errors/skipped, pacote Spring Boot gerado.
- `docker build --tag ledgerlens-backend:phase5 --file backend/Dockerfile .`: sucesso; JRE 25.0.4.1, UID/GID 10001, heap relativo ao limite cgroup; healthcheck presente.
- Configuração `frontend/vercel.json` validada como JSON; `render.yaml`, Dependabot e workflow validados como YAML.
- `mvn verify` sem filtro: 29 testes, 14 errors porque Testcontainers não conseguiu conectar ao Docker Desktop (API retornou HTTP 400/sem ambiente Docker válido); ver summary.

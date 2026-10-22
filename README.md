# LedgerLens

Sistema de conciliação financeira (ERP × extrato bancário), com importação de CSV e conciliação automatizada.

## Como rodar

Pré-requisitos: Docker, Java 25 (LTS) e Node 20+.

```bash
# 1. Banco de dados (PostgreSQL 16)
docker compose up -d

# 2. Backend (porta 8080) — o Flyway aplica as migrations pendentes
cd backend
./mvnw spring-boot:run

# 3. Frontend (porta 5173)
cd frontend
npm install
npm run dev
```

Testes do backend (exigem Docker, usam Testcontainers): `./mvnw test`

## Estrutura

```
backend/src/main/java/com/ledgerlens
├── domain          entidades (ImportBatch, FinancialTransaction, ReconciliationResult) e enums
├── application     serviços e regras de negócio, incluindo parser e conciliador CSV
├── infrastructure  repositórios Spring Data
├── presentation    controller REST e tratamento de erros
└── config          CORS
frontend/src        App, componentes, cliente da API
```

## Endpoints

| Método | Rota                              | Observação                                        |
|--------|-----------------------------------|---------------------------------------------------|
| POST   | `/api/v1/batches/upload`          | multipart: `file` + `sourceType` (`ERP` \| `BANK`) |
| POST   | `/api/v1/reconciliations/execute` | dispara a conciliação                             |
| GET    | `/api/v1/reconciliations/latest`  | resultado paginado (`status`, `page`, `size`)     |
| GET    | `/api/v1/reconciliations/latest/export` | exporta o resultado em CSV protegido          |
| DELETE | `/api/v1/workspace`               | apaga os dados do espaço de trabalho (204)      |

Todas as requisições da API exigem `X-Workspace-Id` com um UUID. O frontend cria e mantém um UUID
local por navegador. Os dados são removidos automaticamente após 24 horas (ajustável por
`ledgerlens.retention-hours`).

Para produção, ative `SPRING_PROFILES_ACTIVE=prod` e configure `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `ALLOWED_ORIGINS` (origens separadas
por vírgulas, sem `*`) e opcionalmente `PORT`. O Spring Boot Actuator fornece a verificação operacional;
somente `/actuator/health` é exposto.

O envio de CSV e a conciliação entre os lotes mais recentes do ERP e do banco são reais. A conciliação
prioriza referência/valor, depois data/valor e então aplica tolerâncias configuráveis em
`ledgerlens.reconciliation`. O CSV exportado usa UTF-8 com BOM, separador `;` e proteção contra fórmulas.

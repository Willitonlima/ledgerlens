# LedgerLens

Sistema de conciliação financeira (ERP × extrato bancário). Esta é a **base inicial**: compila, sobe e é navegável,
mas as regras de negócio críticas estão propositalmente sem implementação.

## Como rodar

Pré-requisitos: Docker, Java 25 (LTS) e Node 20+.

```bash
# 1. Banco de dados (PostgreSQL 16)
docker compose up -d

# 2. Backend (porta 8080) — o Flyway aplica a migration V1 na primeira subida
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
├── application     serviços (interfaces + implementações stub) e DTOs
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
| GET    | `/api/v1/reconciliations/latest`  | resultado consolidado                             |

Hoje as três respostas são **simuladas** (campo `simulated: true`), e o frontend mostra um aviso enquanto isso valer.

## O que falta implementar (procure por `TODO: Implementar lógica de negócio`)

1. `DefaultCsvParserService.parse` — lança `UnsupportedOperationException`. Parsing do CSV (separador, charset, formatos de data e decimal).
2. `DefaultBatchImportService.receive` — criar o `ImportBatch`, chamar o parser, validar e persistir os lançamentos.
3. `DefaultReconciliationService.execute` — algoritmo de conciliação (critérios de match, tolerâncias, MATCHED/DIVERGENT/MISSING) e gravação dos `ReconciliationResult`.
4. `DefaultReconciliationService.getLatest` — agregar a última execução (hoje devolve dados fixos).
5. Ao terminar, remova o campo `simulated` dos DTOs e o aviso em `App.jsx`.

Qualquer `UnsupportedOperationException` vira HTTP 501 com mensagem legível (`ApiExceptionHandler`), e o frontend a exibe.

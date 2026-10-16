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

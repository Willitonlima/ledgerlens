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

O envio de arquivos CSV está implementado. A conciliação ainda retorna dados simulados.

### Importação de CSV

O upload aceita arquivos `.csv` de até 10 MB e 50.000 registros, com cabeçalho identificando data,
descrição e valor; referência é opcional. A importação reconhece separadores `;` e `,`, datas `dd/MM/yyyy`
ou `yyyy-MM-dd`, CSV em UTF-8 (com ou sem BOM) ou ISO-8859-1, e valores como `1.234,56` e `1234.56`.
Valores negativos (inclusive entre parênteses) são débitos; valores positivos são créditos. A convenção
é a mesma para os arquivos do ERP e do banco. Reenvios idênticos da mesma origem no mesmo banco/workspace
são idempotentes.

## O que falta implementar (procure por `TODO: Implementar lógica de negócio`)

1. `DefaultReconciliationService.execute` — algoritmo de conciliação (critérios de match, tolerâncias, MATCHED/DIVERGENT/MISSING) e gravação dos `ReconciliationResult`.
2. `DefaultReconciliationService.getLatest` — agregar a última execução (hoje devolve dados fixos).

Qualquer `UnsupportedOperationException` vira HTTP 501 com mensagem legível (`ApiExceptionHandler`), e o frontend a exibe.

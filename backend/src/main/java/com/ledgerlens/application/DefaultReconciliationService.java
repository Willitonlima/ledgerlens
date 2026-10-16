package com.ledgerlens.application;

import com.ledgerlens.application.dto.ExecutionReceipt;
import com.ledgerlens.application.dto.ReconciliationItem;
import com.ledgerlens.application.dto.ReconciliationSummary;
import com.ledgerlens.domain.MatchStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class DefaultReconciliationService implements ReconciliationService {

    @Override
    public ExecutionReceipt execute() {
        // TODO: Implementar lógica de negócio.
        //  - Carregar os lançamentos ERP e BANK (FinancialTransactionRepository).
        //  - Definir os critérios de match (data, valor, descrição, referência externa) e tolerâncias.
        //  - Classificar cada par como MATCHED / DIVERGENT / MISSING.
        //  - Persistir os ReconciliationResult com um mesmo runId.
        //
        // Resposta SIMULADA: nenhum cálculo é feito.
        return new ExecutionReceipt(UUID.randomUUID(), Instant.now(), true);
    }

    @Override
    public ReconciliationSummary getLatest() {
        // TODO: Implementar lógica de negócio.
        //  Buscar o runId mais recente em ReconciliationResultRepository e agregar:
        //  totais por origem, contagem de divergentes e ausentes, e as linhas do resultado.
        //
        // Resposta SIMULADA: dados fixos e arbitrários, apenas para a interface ter o que exibir.
        List<ReconciliationItem> items = List.of(
                item("2026-10-01", "Pagamento fornecedor Alfa Logística", "-4250.00", MatchStatus.MATCHED),
                item("2026-10-02", "Recebimento cliente Beta Comércio", "8900.00", MatchStatus.MATCHED),
                item("2026-10-03", "Tarifa bancária mensal", "-49.90", MatchStatus.DIVERGENT),
                item("2026-10-05", "Transferência Gama Serviços", "-1320.00", MatchStatus.MISSING),
                item("2026-10-07", "Pagamento de energia elétrica", "-780.35", MatchStatus.MATCHED),
                item("2026-10-09", "Recebimento cliente Delta Ltda", "3150.00", MatchStatus.DIVERGENT)
        );

        return new ReconciliationSummary(
                Instant.now(),
                new BigDecimal("18495.35"),
                new BigDecimal("17175.35"),
                2,
                1,
                items,
                true
        );
    }

    private static ReconciliationItem item(String date, String description, String amount, MatchStatus status) {
        return new ReconciliationItem(UUID.randomUUID(), LocalDate.parse(date), description, new BigDecimal(amount), status);
    }
}

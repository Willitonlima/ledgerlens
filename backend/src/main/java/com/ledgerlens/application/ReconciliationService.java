package com.ledgerlens.application;

import com.ledgerlens.application.dto.ExecutionReceipt;
import com.ledgerlens.application.dto.ReconciliationSummary;

/** Regras de conciliação entre lançamentos do ERP e do extrato bancário. */
public interface ReconciliationService {

    /** Executa a rotina de conciliação sobre os lotes importados. */
    ExecutionReceipt execute();

    /** Devolve o resultado consolidado da execução mais recente. */
    ReconciliationSummary getLatest();
}

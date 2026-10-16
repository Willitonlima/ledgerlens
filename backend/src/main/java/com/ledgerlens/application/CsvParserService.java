package com.ledgerlens.application;

import com.ledgerlens.domain.FinancialTransaction;
import com.ledgerlens.domain.ImportBatch;

import java.io.InputStream;
import java.util.List;

/** Transforma o conteúdo de um CSV em lançamentos financeiros. */
public interface CsvParserService {

    /**
     * Lê o CSV e devolve os lançamentos correspondentes, associados ao lote informado.
     *
     * @param csv   conteúdo do arquivo
     * @param batch lote ao qual os lançamentos pertencem (define também o {@code SourceType})
     */
    List<FinancialTransaction> parse(InputStream csv, ImportBatch batch);
}

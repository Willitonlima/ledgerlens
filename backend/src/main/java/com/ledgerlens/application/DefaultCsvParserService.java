package com.ledgerlens.application;

import com.ledgerlens.domain.FinancialTransaction;
import com.ledgerlens.domain.ImportBatch;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
public class DefaultCsvParserService implements CsvParserService {

    @Override
    public List<FinancialTransaction> parse(InputStream csv, ImportBatch batch) {
        // TODO: Implementar lógica de negócio.
        //  Pontos a decidir: leitura do cabeçalho, separador, charset, formato de data,
        //  formato decimal (1.234,56 x 1234.56), tratamento de linhas inválidas.
        throw new UnsupportedOperationException("A ser implementado pelo desenvolvedor");
    }
}

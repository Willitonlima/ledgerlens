package com.ledgerlens.application;

import com.ledgerlens.application.dto.BatchUploadReceipt;
import com.ledgerlens.domain.BatchStatus;
import com.ledgerlens.domain.SourceType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class DefaultBatchImportService implements BatchImportService {

    @Override
    public BatchUploadReceipt receive(MultipartFile file, SourceType sourceType) {
        // TODO: Implementar lógica de negócio.
        //  1. Criar e salvar um ImportBatch (ImportBatchRepository).
        //  2. Chamar CsvParserService.parse(file.getInputStream(), batch).
        //  3. Validar os registros e salvar os lançamentos (FinancialTransactionRepository).
        //  4. Atualizar status e totalRecords do lote.
        //
        // Resposta SIMULADA: nada é lido nem gravado. Substitua pelo fluxo acima.
        return new BatchUploadReceipt(
                UUID.randomUUID(),
                sourceType,
                file.getOriginalFilename(),
                BatchStatus.RECEIVED,
                0,
                true
        );
    }
}

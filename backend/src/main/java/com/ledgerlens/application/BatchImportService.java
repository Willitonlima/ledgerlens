package com.ledgerlens.application;

import com.ledgerlens.application.dto.BatchUploadReceipt;
import com.ledgerlens.domain.SourceType;
import org.springframework.web.multipart.MultipartFile;

/** Recebe um CSV, registra o lote e persiste os lançamentos. */
public interface BatchImportService {

    BatchUploadReceipt receive(MultipartFile file, SourceType sourceType);
}

package com.ledgerlens.presentation;

import com.ledgerlens.application.BatchImportService;
import com.ledgerlens.application.ReconciliationService;
import com.ledgerlens.application.dto.BatchUploadReceipt;
import com.ledgerlens.application.dto.ExecutionReceipt;
import com.ledgerlens.application.dto.ReconciliationSummary;
import com.ledgerlens.domain.SourceType;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ReconciliationController {

    private final BatchImportService batchImportService;
    private final ReconciliationService reconciliationService;

    public ReconciliationController(BatchImportService batchImportService,
                                    ReconciliationService reconciliationService) {
        this.batchImportService = batchImportService;
        this.reconciliationService = reconciliationService;
    }

    /** Recebe um CSV. Exemplo: {@code POST /api/v1/batches/upload?sourceType=ERP} (multipart, campo "file"). */
    @PostMapping(value = "/batches/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchUploadReceipt> upload(@RequestParam("file") MultipartFile file,
                                                     @RequestParam("sourceType") SourceType sourceType) {
        BatchUploadReceipt receipt = batchImportService.receive(file, sourceType);
        return ResponseEntity.status(receipt.alreadyImported() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(receipt);
    }

    /** Dispara a rotina de conciliação. */
    @PostMapping("/reconciliations/execute")
    public ExecutionReceipt execute() {
        return reconciliationService.execute();
    }

    /** Resultado consolidado da última conciliação. */
    @GetMapping("/reconciliations/latest")
    public ReconciliationSummary latest() {
        return reconciliationService.getLatest();
    }
}

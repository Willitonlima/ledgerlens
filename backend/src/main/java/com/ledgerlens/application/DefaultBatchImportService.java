package com.ledgerlens.application;

import com.ledgerlens.application.dto.BatchUploadReceipt;
import com.ledgerlens.domain.BatchStatus;
import com.ledgerlens.domain.FinancialTransaction;
import com.ledgerlens.domain.ImportBatch;
import com.ledgerlens.domain.SourceType;
import com.ledgerlens.infrastructure.persistence.FinancialTransactionRepository;
import com.ledgerlens.infrastructure.persistence.ImportBatchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DefaultBatchImportService implements BatchImportService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/csv", "application/csv", "application/vnd.ms-excel",
            "text/plain", "application/octet-stream");

    private final CsvParserService csvParserService;
    private final ImportBatchRepository importBatchRepository;
    private final FinancialTransactionRepository financialTransactionRepository;

    public DefaultBatchImportService(CsvParserService csvParserService,
                                     ImportBatchRepository importBatchRepository,
                                     FinancialTransactionRepository financialTransactionRepository) {
        this.csvParserService = csvParserService;
        this.importBatchRepository = importBatchRepository;
        this.financialTransactionRepository = financialTransactionRepository;
    }

    @Override
    @Transactional
    public BatchUploadReceipt receive(MultipartFile file, SourceType sourceType) {
        validateFile(file);
        String fileHash = calculateHash(file);
        var existingBatch = importBatchRepository.findBySourceTypeAndFileHash(sourceType, fileHash);
        if (existingBatch.isPresent()) {
            ImportBatch batch = existingBatch.get();
            return receipt(batch, true, "Este arquivo já havia sido importado.");
        }

        ImportBatch batch = new ImportBatch();
        batch.setSourceType(sourceType);
        batch.setFileName(file.getOriginalFilename());
        batch.setFileHash(fileHash);

        List<FinancialTransaction> transactions;
        try {
            transactions = csvParserService.parse(file.getInputStream(), batch);
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível ler o arquivo enviado.", exception);
        }

        batch = importBatchRepository.save(batch);
        financialTransactionRepository.saveAll(transactions);
        batch.setStatus(BatchStatus.PROCESSED);
        batch.setTotalRecords(transactions.size());
        batch = importBatchRepository.save(batch);
        return receipt(batch, false, "Arquivo importado com sucesso.");
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException(HttpStatus.BAD_REQUEST, "O arquivo CSV não pode estar vazio.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidUploadException(HttpStatus.BAD_REQUEST, "O arquivo excede o limite de 10 MB.");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new InvalidUploadException(HttpStatus.BAD_REQUEST, "O arquivo deve ter a extensão .csv.");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_CONTENT_TYPES.contains(mediaType)) {
                throw new InvalidUploadException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "O tipo de conteúdo do arquivo não é compatível com CSV.");
            }
        }
    }

    private static String calculateHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            long totalBytes = 0;
            try (InputStream input = file.getInputStream()) {
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    totalBytes += bytesRead;
                    if (totalBytes > MAX_FILE_SIZE) {
                        throw new InvalidUploadException(
                                HttpStatus.BAD_REQUEST, "O arquivo excede o limite de 10 MB.");
                    }
                    digest.update(buffer, 0, bytesRead);
                }
            }
            if (totalBytes == 0) {
                throw new InvalidUploadException(HttpStatus.BAD_REQUEST, "O arquivo CSV não pode estar vazio.");
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("O algoritmo SHA-256 não está disponível.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível ler o arquivo enviado.", exception);
        }
    }

    private static BatchUploadReceipt receipt(ImportBatch batch, boolean alreadyImported, String message) {
        return new BatchUploadReceipt(
                batch.getId(),
                batch.getSourceType(),
                batch.getFileName(),
                batch.getStatus(),
                batch.getTotalRecords(),
                alreadyImported,
                message);
    }
}

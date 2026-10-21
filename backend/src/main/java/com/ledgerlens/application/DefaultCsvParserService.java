package com.ledgerlens.application;

import com.ledgerlens.domain.FinancialTransaction;
import com.ledgerlens.domain.ImportBatch;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DefaultCsvParserService implements CsvParserService {

    private static final int MAX_RECORDS = 50_000;
    private static final int MAX_ERRORS = 50;
    private static final DateTimeFormatter BRAZILIAN_DATE =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern BRAZILIAN_AMOUNT =
            Pattern.compile("(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+)(?:,[0-9]{1,2})?");
    private static final Pattern DECIMAL_AMOUNT = Pattern.compile("[0-9]+(?:\\.[0-9]{1,2})?");

    @Override
    public List<FinancialTransaction> parse(InputStream csv, ImportBatch batch) {
        List<FinancialTransaction> transactions = new ArrayList<>();
        List<CsvValidationException.CsvError> errors = new ArrayList<>();
        int totalErrors = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csv, StandardCharsets.ISO_8859_1))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new CsvValidationException(
                        "O arquivo não contém cabeçalho.",
                        List.of(error(1, "header", "O arquivo não contém cabeçalho.")),
                        1);
            }
            if (headerLine.startsWith("\u00EF\u00BB\u00BF")) {
                headerLine = headerLine.substring(3);
            }

            char delimiter = detectDelimiter(headerLine);
            List<String> headers;
            try {
                headers = parseRecord(headerLine, delimiter);
            } catch (IllegalArgumentException exception) {
                throw invalid(List.of(error(1, "header", "O cabeçalho CSV tem aspas inválidas.")), 1);
            }
            Map<String, Integer> columns = mapColumns(headers);
            List<CsvValidationException.CsvError> headerErrors = validateHeaders(columns, headers.size());
            if (!headerErrors.isEmpty()) {
                throw invalid(headerErrors, headerErrors.size());
            }

            int line = 1;
            int recordsSeen = 0;
            String linePart;
            while ((linePart = reader.readLine()) != null) {
                int startLine = line + 1;
                line++;
                StringBuilder record = new StringBuilder(linePart);
                while (!isRecordComplete(record)) {
                    String continuation = reader.readLine();
                    if (continuation == null) {
                        totalErrors++;
                        addError(errors, error(startLine, "row", "Campo entre aspas não foi fechado."));
                        break;
                    }
                    record.append('\n').append(continuation);
                    line++;
                }
                if (!isRecordComplete(record)) {
                    continue;
                }
                if (record.toString().isBlank()) {
                    continue;
                }

                recordsSeen++;
                if (recordsSeen > MAX_RECORDS) {
                    throw new CsvValidationException(
                            "O arquivo excede o limite de 50.000 linhas.",
                            List.of(error(startLine, "row", "O arquivo excede o limite de 50.000 linhas.")),
                            1);
                }

                List<String> fields;
                try {
                    fields = parseRecord(record.toString(), delimiter);
                } catch (IllegalArgumentException exception) {
                    totalErrors++;
                    addError(errors, error(startLine, "row", "Formato de aspas inválido."));
                    continue;
                }

                List<CsvValidationException.CsvError> rowErrors = new ArrayList<>();
                FinancialTransaction transaction =
                        parseTransaction(fields, columns, headers.size(), batch, startLine, rowErrors);
                totalErrors += rowErrors.size();
                for (CsvValidationException.CsvError rowError : rowErrors) {
                    addError(errors, rowError);
                }
                if (transaction != null) {
                    transactions.add(transaction);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível ler o arquivo CSV.", exception);
        }

        if (!errors.isEmpty()) {
            throw invalid(errors, Math.max(errors.size(), totalErrors));
        }
        return transactions;
    }

    private FinancialTransaction parseTransaction(
            List<String> fields,
            Map<String, Integer> columns,
            int expectedColumns,
            ImportBatch batch,
            int line,
            List<CsvValidationException.CsvError> rowErrors) {
        if (fields.size() != expectedColumns) {
            rowErrors.add(error(line, "row", "A quantidade de colunas não corresponde ao cabeçalho."));
        }

        LocalDate date = null;
        String dateValue = value(fields, columns.get("date"));
        if (dateValue.isBlank()) {
            rowErrors.add(error(line, "date", "A data é obrigatória."));
        } else {
            try {
                date = parseDate(dateValue);
            } catch (DateTimeParseException exception) {
                rowErrors.add(error(line, "date", "Use uma data válida no formato dd/MM/yyyy ou yyyy-MM-dd."));
            }
        }

        String description = value(fields, columns.get("description")).trim();
        if (description.isBlank()) {
            rowErrors.add(error(line, "description", "A descrição é obrigatória."));
        } else if (description.length() > 500) {
            rowErrors.add(error(line, "description", "A descrição deve ter no máximo 500 caracteres."));
        }

        BigDecimal amount = null;
        String amountValue = value(fields, columns.get("amount"));
        if (amountValue.isBlank()) {
            rowErrors.add(error(line, "amount", "O valor é obrigatório."));
        } else {
            try {
                amount = parseAmount(amountValue);
            } catch (NumberFormatException exception) {
                rowErrors.add(error(line, "amount", "Use um valor válido, como 1.234,56 ou 1234.56."));
            }
        }

        String reference = columns.containsKey("reference")
                ? value(fields, columns.get("reference")).trim()
                : null;
        if (reference != null && reference.length() > 100) {
            rowErrors.add(error(line, "reference", "A referência deve ter no máximo 100 caracteres."));
        }

        if (!rowErrors.isEmpty()) {
            return null;
        }

        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setBatch(batch);
        transaction.setSourceType(batch.getSourceType());
        transaction.setTransactionDate(date);
        transaction.setDescription(description);
        transaction.setAmount(amount);
        transaction.setExternalReference(reference == null || reference.isBlank() ? null : reference);
        return transaction;
    }

    private static LocalDate parseDate(String value) {
        String date = decodeField(value).trim();
        try {
            return LocalDate.parse(date, BRAZILIAN_DATE);
        } catch (DateTimeParseException ignored) {
            return LocalDate.parse(date, ISO_DATE);
        }
    }

    private static BigDecimal parseAmount(String value) {
        String amount = decodeField(value).trim();
        if (amount.regionMatches(true, 0, "R$", 0, 2)) {
            amount = amount.substring(2);
        }
        amount = amount.replaceAll("\\s+", "");
        boolean parenthesized = amount.startsWith("(") && amount.endsWith(")");
        if (parenthesized) {
            amount = amount.substring(1, amount.length() - 1);
        } else if (amount.startsWith("(") || amount.endsWith(")")) {
            throw new NumberFormatException("Parênteses inválidos");
        }
        if (amount.regionMatches(true, 0, "R$", 0, 2)) {
            amount = amount.substring(2).replaceAll("\\s+", "");
        }
        if (amount.toUpperCase(Locale.ROOT).contains("R$")) {
            throw new NumberFormatException("O símbolo R$ deve ser um prefixo");
        }
        if (parenthesized && (amount.startsWith("-") || amount.startsWith("+"))) {
            throw new NumberFormatException("Sinal e parênteses não podem ser combinados");
        }
        boolean negative = parenthesized || amount.startsWith("-");
        if (amount.startsWith("-") || amount.startsWith("+")) {
            amount = amount.substring(1);
        }

        String normalized;
        if (BRAZILIAN_AMOUNT.matcher(amount).matches()) {
            normalized = amount.replace(".", "").replace(',', '.');
        } else if (DECIMAL_AMOUNT.matcher(amount).matches()) {
            normalized = amount;
        } else {
            throw new NumberFormatException("Valor inválido");
        }
        BigDecimal parsed = new BigDecimal(normalized).setScale(2);
        if (parsed.precision() > 19) {
            throw new NumberFormatException("Valor excede a precisão permitida");
        }
        return negative ? parsed.negate() : parsed;
    }

    private static Map<String, Integer> mapColumns(List<String> headers) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String header = normalizeHeader(headers.get(index));
            switch (header) {
                case "data", "date" -> columns.putIfAbsent("date", index);
                case "descricao", "historico", "description" -> columns.putIfAbsent("description", index);
                case "valor", "amount" -> columns.putIfAbsent("amount", index);
                case "referencia", "documento", "reference" -> columns.putIfAbsent("reference", index);
                default -> {
                }
            }
        }
        return columns;
    }

    private static List<CsvValidationException.CsvError> validateHeaders(Map<String, Integer> columns, int count) {
        List<CsvValidationException.CsvError> errors = new ArrayList<>();
        for (String required : List.of("date", "description", "amount")) {
            if (!columns.containsKey(required)) {
                errors.add(error(1, "header", "Cabeçalho obrigatório ausente: " + required + "."));
            }
        }
        if (count < 3) {
            errors.add(error(1, "header", "O cabeçalho deve conter data, descrição e valor."));
        }
        return errors;
    }

    private static String normalizeHeader(String value) {
        String decoded = decodeField(value).trim().toLowerCase(Locale.ROOT);
        return Normalizer.normalize(decoded, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String decodeField(String rawValue) {
        byte[] bytes = new byte[rawValue.length()];
        for (int index = 0; index < rawValue.length(); index++) {
            char character = rawValue.charAt(index);
            if (character > 0xFF) {
                return rawValue;
            }
            bytes[index] = (byte) character;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private static char detectDelimiter(String header) {
        int semicolons = countOutsideQuotes(header, ';');
        int commas = countOutsideQuotes(header, ',');
        return semicolons > commas ? ';' : ',';
    }

    private static int countOutsideQuotes(String value, char target) {
        int count = 0;
        boolean quoted = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < value.length() && value.charAt(index + 1) == '"') {
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && character == target) {
                count++;
            }
        }
        return count;
    }

    private static List<String> parseRecord(String record, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        for (int index = 0; index < record.length(); index++) {
            char character = record.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < record.length() && record.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        closedQuote = true;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
                closedQuote = false;
            } else if (character == '"' && field.isEmpty() && !closedQuote) {
                quoted = true;
            } else if (closedQuote && !Character.isWhitespace(character)) {
                throw new IllegalArgumentException("Conteúdo após aspas");
            } else {
                field.append(character);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Aspas não fechadas");
        }
        fields.add(field.toString());
        return fields;
    }

    private static boolean isRecordComplete(CharSequence record) {
        boolean quoted = false;
        for (int index = 0; index < record.length(); index++) {
            if (record.charAt(index) == '"') {
                if (quoted && index + 1 < record.length() && record.charAt(index + 1) == '"') {
                    index++;
                } else {
                    quoted = !quoted;
                }
            }
        }
        return !quoted;
    }

    private static String value(List<String> fields, Integer index) {
        if (index == null || index < 0 || index >= fields.size()) {
            return "";
        }
        return decodeField(fields.get(index));
    }

    private static CsvValidationException.CsvError error(int line, String field, String message) {
        return new CsvValidationException.CsvError(line, field, message);
    }

    private static void addError(List<CsvValidationException.CsvError> errors,
                                 CsvValidationException.CsvError error) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(error);
        }
    }

    private static CsvValidationException invalid(List<CsvValidationException.CsvError> errors, int total) {
        return new CsvValidationException("O arquivo contém registros inválidos.", errors, total);
    }
}

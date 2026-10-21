package com.ledgerlens.application;

import com.ledgerlens.config.ReconciliationProperties;
import com.ledgerlens.domain.FinancialTransaction;
import com.ledgerlens.domain.MatchStatus;
import com.ledgerlens.domain.ReconciliationResult;
import com.ledgerlens.domain.SourceType;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

public class ReconciliationMatcher {

    private static final Pattern PUNCTUATION = Pattern.compile("\\p{P}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final ReconciliationProperties properties;

    public ReconciliationMatcher(ReconciliationProperties properties) {
        this.properties = properties;
    }

    public List<ReconciliationResult> reconcile(
            List<FinancialTransaction> erpTransactions,
            List<FinancialTransaction> bankTransactions,
            UUID runId,
            java.time.Instant executedAt) {
        Set<UUID> usedBankIds = new HashSet<>();
        Map<ReferenceAmountKey, List<FinancialTransaction>> referenceIndex = new HashMap<>();
        Map<DateAmountKey, List<FinancialTransaction>> exactIndex = new HashMap<>();
        TreeMap<BigDecimal, List<FinancialTransaction>> amountIndex = new TreeMap<>();
        Map<BigDecimal, TreeMap<java.time.LocalDate, List<FinancialTransaction>>> amountDateIndex = new TreeMap<>();
        Map<UUID, Integer> bankOrder = new HashMap<>();

        for (int index = 0; index < bankTransactions.size(); index++) {
            FinancialTransaction bank = bankTransactions.get(index);
            bankOrder.put(bank.getId(), index);
            if (bank.getExternalReference() != null && !bank.getExternalReference().isBlank()) {
                referenceIndex.computeIfAbsent(
                        new ReferenceAmountKey(normalizeReference(bank.getExternalReference()), bank.getAmount()),
                        ignored -> new ArrayList<>()).add(bank);
            }
            exactIndex.computeIfAbsent(new DateAmountKey(bank.getTransactionDate(), bank.getAmount()),
                    ignored -> new ArrayList<>()).add(bank);
            amountIndex.computeIfAbsent(bank.getAmount(), ignored -> new ArrayList<>()).add(bank);
            amountDateIndex.computeIfAbsent(bank.getAmount(), ignored -> new TreeMap<>())
                    .computeIfAbsent(bank.getTransactionDate(), ignored -> new ArrayList<>()).add(bank);
        }

        List<ReconciliationResult> results = new ArrayList<>();
        for (FinancialTransaction erp : erpTransactions) {
            FinancialTransaction bank = firstAvailable(
                    erp.getExternalReference() == null || erp.getExternalReference().isBlank()
                            ? List.of()
                            : referenceIndex.getOrDefault(
                                    new ReferenceAmountKey(normalizeReference(erp.getExternalReference()), erp.getAmount()),
                                    List.of()),
                    usedBankIds);
            if (bank != null) {
                usedBankIds.add(bank.getId());
                results.add(result(runId, executedAt, erp, bank, MatchStatus.MATCHED,
                        difference(erp, bank), "Correspondência por referência e valor."));
                continue;
            }

            List<FinancialTransaction> exactCandidates = exactIndex.getOrDefault(
                    new DateAmountKey(erp.getTransactionDate(), erp.getAmount()), List.of());
            bank = bestCandidate(erp, exactCandidates, usedBankIds, bankOrder);
            if (bank != null) {
                usedBankIds.add(bank.getId());
                results.add(result(runId, executedAt, erp, bank, MatchStatus.MATCHED,
                        BigDecimal.ZERO.setScale(2), "Correspondência por data e valor."));
                continue;
            }

            bank = bestTolerantCandidate(erp, amountIndex, amountDateIndex, usedBankIds, bankOrder);
            if (bank != null) {
                usedBankIds.add(bank.getId());
                BigDecimal difference = difference(erp, bank);
                boolean withinCentTolerance = difference.abs().compareTo(properties.getAmountTolerance()) <= 0;
                boolean sameDate = erp.getTransactionDate().equals(bank.getTransactionDate());
                MatchStatus status = sameDate && withinCentTolerance ? MatchStatus.MATCHED : MatchStatus.DIVERGENT;
                String notes = status == MatchStatus.MATCHED
                        ? "Correspondência com tolerância de valor."
                        : divergenceNotes(erp, bank, withinCentTolerance);
                results.add(result(runId, executedAt, erp, bank, status, difference, notes));
            } else {
                results.add(result(runId, executedAt, erp, null, MatchStatus.MISSING, null, "Somente no ERP."));
            }
        }

        for (FinancialTransaction bank : bankTransactions) {
            if (!usedBankIds.contains(bank.getId())) {
                results.add(result(runId, executedAt, null, bank, MatchStatus.MISSING, null, "Somente no banco."));
            }
        }
        return results;
    }

    private FinancialTransaction bestTolerantCandidate(
            FinancialTransaction erp,
            TreeMap<BigDecimal, List<FinancialTransaction>> amountIndex,
            Map<BigDecimal, TreeMap<java.time.LocalDate, List<FinancialTransaction>>> amountDateIndex,
            Set<UUID> usedBankIds,
            Map<UUID, Integer> bankOrder) {
        Set<FinancialTransaction> candidates = new LinkedHashSet<>();
        TreeMap<java.time.LocalDate, List<FinancialTransaction>> sameAmount =
                amountDateIndex.get(erp.getAmount());
        if (sameAmount != null) {
            java.time.LocalDate from = erp.getTransactionDate().minusDays(properties.getDateToleranceDays());
            java.time.LocalDate through = erp.getTransactionDate().plusDays(properties.getDateToleranceDays());
            sameAmount.subMap(from, true, through, true).values().forEach(candidates::addAll);
        }

        BigDecimal tolerance = properties.getAmountTolerance();
        if (tolerance.signum() > 0) {
            BigDecimal low = erp.getAmount().subtract(tolerance);
            BigDecimal high = erp.getAmount().add(tolerance);
            amountIndex.subMap(low, true, high, true).values().forEach(candidates::addAll);
        }

        return bestCandidate(erp, candidates, usedBankIds, bankOrder,
                candidate -> {
                    BigDecimal amountDifference = difference(erp, candidate).abs();
                    boolean sameAmountNearbyDate = amountDifference.signum() == 0
                            && Math.abs(ChronoUnit.DAYS.between(
                                    erp.getTransactionDate(), candidate.getTransactionDate()))
                            <= properties.getDateToleranceDays();
                    boolean amountWithinTolerance = amountDifference.compareTo(tolerance) <= 0;
                    return (sameAmountNearbyDate || amountWithinTolerance)
                            && similarity(erp.getDescription(), candidate.getDescription())
                            >= properties.getMinimumDescriptionSimilarity();
                });
    }

    private FinancialTransaction bestCandidate(
            FinancialTransaction erp,
            Iterable<FinancialTransaction> candidates,
            Set<UUID> usedBankIds,
            Map<UUID, Integer> bankOrder) {
        return bestCandidate(erp, candidates, usedBankIds, bankOrder, ignored -> true);
    }

    private FinancialTransaction bestCandidate(
            FinancialTransaction erp,
            Iterable<FinancialTransaction> candidates,
            Set<UUID> usedBankIds,
            Map<UUID, Integer> bankOrder,
            java.util.function.Predicate<FinancialTransaction> eligible) {
        FinancialTransaction best = null;
        double bestSimilarity = -1;
        for (FinancialTransaction candidate : candidates) {
            if (usedBankIds.contains(candidate.getId()) || !eligible.test(candidate)) {
                continue;
            }
            double candidateSimilarity = similarity(erp.getDescription(), candidate.getDescription());
            if (best == null || candidateSimilarity > bestSimilarity
                    || (Double.compare(candidateSimilarity, bestSimilarity) == 0
                    && bankOrder.get(candidate.getId()) < bankOrder.get(best.getId()))) {
                best = candidate;
                bestSimilarity = candidateSimilarity;
            }
        }
        return best;
    }

    private static FinancialTransaction firstAvailable(
            List<FinancialTransaction> candidates,
            Set<UUID> usedBankIds) {
        for (FinancialTransaction candidate : candidates) {
            if (!usedBankIds.contains(candidate.getId())) {
                return candidate;
            }
        }
        return null;
    }

    static String normalizeDescription(String description) {
        String decomposed = Normalizer.normalize(description.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        String withoutAccents = decomposed.replaceAll("\\p{M}", "");
        return WHITESPACE.matcher(PUNCTUATION.matcher(withoutAccents).replaceAll(" "))
                .replaceAll(" ").trim();
    }

    static double similarity(String left, String right) {
        String first = normalizeDescription(left);
        String second = normalizeDescription(right);
        if (first.equals(second)) {
            return 1.0;
        }
        int maximumLength = Math.max(first.length(), second.length());
        if (maximumLength == 0) {
            return 1.0;
        }
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int column = 0; column <= second.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= first.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= second.length(); column++) {
                int substitution = previous[column - 1]
                        + (first.charAt(row - 1) == second.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(Math.min(previous[column] + 1, current[column - 1] + 1), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return 1.0 - (double) previous[second.length()] / maximumLength;
    }

    private static String normalizeReference(String reference) {
        return reference.trim().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal difference(FinancialTransaction erp, FinancialTransaction bank) {
        return erp.getAmount().subtract(bank.getAmount()).setScale(2);
    }

    private static String divergenceNotes(
            FinancialTransaction erp,
            FinancialTransaction bank,
            boolean valueWithinTolerance) {
        boolean dateDiffers = !erp.getTransactionDate().equals(bank.getTransactionDate());
        boolean valueDiffers = !valueWithinTolerance;
        if (dateDiffers && valueDiffers) {
            return "Divergem a data e o valor.";
        }
        if (dateDiffers) {
            return "Diverge a data.";
        }
        return "Diverge o valor.";
    }

    private static ReconciliationResult result(
            UUID runId,
            java.time.Instant executedAt,
            FinancialTransaction erp,
            FinancialTransaction bank,
            MatchStatus status,
            BigDecimal difference,
            String notes) {
        ReconciliationResult result = new ReconciliationResult();
        result.setRunId(runId);
        result.setExecutedAt(executedAt);
        result.setErpTransaction(erp);
        result.setBankTransaction(bank);
        result.setStatus(status);
        result.setDifferenceAmount(difference);
        result.setNotes(notes);
        return result;
    }

    private record ReferenceAmountKey(String reference, BigDecimal amount) {
    }

    private record DateAmountKey(java.time.LocalDate date, BigDecimal amount) {
    }
}

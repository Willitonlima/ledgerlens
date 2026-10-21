package com.ledgerlens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "ledgerlens.reconciliation")
public class ReconciliationProperties {

    private int dateToleranceDays = 3;
    private BigDecimal amountTolerance = new BigDecimal("0.01");
    private double minimumDescriptionSimilarity = 0.6;

    public int getDateToleranceDays() {
        return dateToleranceDays;
    }

    public void setDateToleranceDays(int dateToleranceDays) {
        this.dateToleranceDays = dateToleranceDays;
    }

    public BigDecimal getAmountTolerance() {
        return amountTolerance;
    }

    public void setAmountTolerance(BigDecimal amountTolerance) {
        this.amountTolerance = amountTolerance;
    }

    public double getMinimumDescriptionSimilarity() {
        return minimumDescriptionSimilarity;
    }

    public void setMinimumDescriptionSimilarity(double minimumDescriptionSimilarity) {
        this.minimumDescriptionSimilarity = minimumDescriptionSimilarity;
    }
}

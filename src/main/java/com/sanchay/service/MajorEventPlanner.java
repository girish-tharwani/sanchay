package com.sanchay.service;

import com.sanchay.model.MajorEvent;
import com.sanchay.model.PlanParameters;
import com.sanchay.model.Transaction;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure major-event forecasting and actual-spend aggregation for financial planning.
 */
public class MajorEventPlanner {

    private final DataStore ds;

    public MajorEventPlanner() {
        this(DataStore.getInstance());
    }

    public MajorEventPlanner(DataStore ds) {
        this.ds = ds;
    }

    public long computeEventForecast(MajorEvent event, LocalDate retirementDate) {
        if (event.getType() == MajorEvent.EventType.ONE_TIME) {
            return event.getAmountPaise();
        }

        LocalDate upperBound = retirementDate;
        if (event.getEndDate() != null) {
            try {
                upperBound = LocalDate.parse(event.getEndDate());
            } catch (Exception ignored) {
            }
        }

        LocalDate from = LocalDate.now();
        if (event.getStartDate() != null) {
            try {
                LocalDate sd = LocalDate.parse(event.getStartDate());
                if (sd.isAfter(from)) from = sd;
            } catch (Exception ignored) {
            }
        }

        if (!from.isBefore(upperBound)) return 0;

        long occurrences = switch (event.getFrequency() != null
                ? event.getFrequency() : MajorEvent.Frequency.MONTHLY) {
            case MONTHLY -> ChronoUnit.MONTHS.between(from, upperBound);
            case QUARTERLY -> ChronoUnit.MONTHS.between(from, upperBound) / 3;
            case YEARLY -> ChronoUnit.YEARS.between(from, upperBound);
        };
        return Math.max(0, occurrences) * event.getAmountPaise();
    }

    public long computeEventActual(MajorEvent event) {
        if (event.getCategoryId() == null) return 0;

        LocalDate startDate = null;
        if (event.getStartDate() != null) {
            try {
                startDate = LocalDate.parse(event.getStartDate());
            } catch (Exception ignored) {
            }
        }

        LocalDate from = startDate;
        return ds.getTransactions().stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE)
                .filter(t -> t.getClassification() != null)
                .filter(t -> event.getCategoryId().equals(t.getClassification().getCategoryId()))
                .filter(t -> event.getSubCategoryId() == null
                        || event.getSubCategoryId().equals(t.getClassification().getSubCategoryId()))
                .filter(t -> from == null || !t.getDate().isBefore(from))
                .mapToLong(Transaction::getAmountPaise)
                .sum();
    }

    public long computeMajorEventsKpi(PlanParameters params, LocalDate retirementDate) {
        if (params == null || params.majorEvents == null) return 0;

        long forecast = params.majorEvents.stream()
                .mapToLong(event -> computeEventForecast(event, retirementDate))
                .sum();
        long actual = params.majorEvents.stream()
                .mapToLong(this::computeEventActual)
                .sum();
        return Math.max(0, forecast - actual);
    }
}

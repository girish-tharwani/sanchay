package com.sanchay.service;

import com.sanchay.model.MajorEvent;
import com.sanchay.model.PlanParameters;
import com.sanchay.model.Transaction;

import java.time.LocalDate;

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
        if (event == null || retirementDate == null) return 0;

        if (event.getType() == MajorEvent.EventType.ONE_TIME) {
            LocalDate startDate = parseDate(event.getStartDate());
            if (startDate != null && startDate.isAfter(retirementDate)) {
                return 0;
            }
            return event.getAmountPaise();
        }

        LocalDate upperBound = retirementDate;
        LocalDate endDate = parseDate(event.getEndDate());
        if (endDate != null && endDate.isBefore(retirementDate)) {
            upperBound = endDate;
        }

        LocalDate startDate = parseDate(event.getStartDate());
        LocalDate from = startDate != null ? startDate : LocalDate.now();
        long occurrences = countRecurringOccurrences(event, from, upperBound.plusDays(1));
        return occurrences * event.getAmountPaise();
    }

    public long computeEventPostRetirementForecast(MajorEvent event,
                                                   LocalDate retirementDate,
                                                   LocalDate projectionEndDate) {
        if (event == null || retirementDate == null || projectionEndDate == null
                || !retirementDate.isBefore(projectionEndDate)) {
            return 0;
        }

        if (event.getType() == MajorEvent.EventType.ONE_TIME) {
            LocalDate startDate = parseDate(event.getStartDate());
            if (startDate != null && startDate.isAfter(retirementDate)
                    && startDate.isBefore(projectionEndDate)) {
                return event.getAmountPaise();
            }
            return 0;
        }

        LocalDate upperBoundExclusive = projectionEndDate;
        LocalDate endDate = parseDate(event.getEndDate());
        if (endDate != null && !endDate.isAfter(retirementDate)) return 0;
        if (endDate != null && endDate.isBefore(upperBoundExclusive)) {
            upperBoundExclusive = endDate.plusDays(1);
        }

        LocalDate startDate = parseDate(event.getStartDate());
        LocalDate from = retirementDate.plusDays(1);
        if (startDate != null && startDate.isAfter(from)) {
            from = startDate;
        }

        long occurrences = countRecurringOccurrences(event, from, upperBoundExclusive);
        return occurrences * event.getAmountPaise();
    }

    public long computeEventActual(MajorEvent event) {
        return computeEventActual(event, null);
    }

    public long computeEventActual(MajorEvent event, LocalDate retirementDate) {
        if (event == null) return 0;
        if (event.getCategoryId() == null) return 0;

        LocalDate startDate = parseDate(event.getStartDate());
        LocalDate upperBound = retirementDate;
        LocalDate endDate = parseDate(event.getEndDate());
        if (retirementDate != null
                && event.getType() == MajorEvent.EventType.RECURRING
                && endDate != null
                && endDate.isBefore(retirementDate)) {
            upperBound = endDate;
        }

        LocalDate actualUpperBound = upperBound;
        if (actualUpperBound != null && startDate != null && startDate.isAfter(actualUpperBound)) {
            return 0;
        }

        LocalDate from = startDate;
        return ds.getTransactions().stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE)
                .filter(t -> t.getClassification() != null)
                .filter(t -> event.getCategoryId().equals(t.getClassification().getCategoryId()))
                .filter(t -> event.getSubCategoryId() == null
                        || event.getSubCategoryId().equals(t.getClassification().getSubCategoryId()))
                .filter(t -> from == null || !t.getDate().isBefore(from))
                .filter(t -> actualUpperBound == null || !t.getDate().isAfter(actualUpperBound))
                .mapToLong(Transaction::getAmountPaise)
                .sum();
    }

    public long computeEventKpi(MajorEvent event, LocalDate retirementDate) {
        return Math.max(0,
                computeEventForecast(event, retirementDate) - computeEventActual(event, retirementDate));
    }

    public long computeMajorEventsKpi(PlanParameters params, LocalDate retirementDate) {
        if (params == null || params.majorEvents == null) return 0;

        return params.majorEvents.stream()
                .mapToLong(event -> computeEventKpi(event, retirementDate))
                .sum();
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(date);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long countRecurringOccurrences(MajorEvent event,
                                           LocalDate rangeStartInclusive,
                                           LocalDate rangeEndExclusive) {
        if (event == null || rangeStartInclusive == null || rangeEndExclusive == null
                || !rangeStartInclusive.isBefore(rangeEndExclusive)) {
            return 0;
        }

        LocalDate occurrence = parseDate(event.getStartDate());
        if (occurrence == null) {
            occurrence = rangeStartInclusive;
        }

        long count = 0;
        while (occurrence.isBefore(rangeEndExclusive)) {
            if (!occurrence.isBefore(rangeStartInclusive)) {
                count++;
            }
            occurrence = advance(occurrence, event.getFrequency());
        }
        return count;
    }

    private LocalDate advance(LocalDate from, MajorEvent.Frequency frequency) {
        return switch (frequency != null ? frequency : MajorEvent.Frequency.MONTHLY) {
            case MONTHLY -> from.plusMonths(1);
            case QUARTERLY -> from.plusMonths(3);
            case YEARLY -> from.plusYears(1);
        };
    }
}

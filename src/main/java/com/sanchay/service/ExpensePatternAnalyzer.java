package com.sanchay.service;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.*;

/**
 * Analyzes historical expense transactions to detect spending patterns —
 * per-sub-category monthly averages, seasonal factors, and trend slope.
 * Pure computation; no UI dependencies.
 */
class ExpensePatternAnalyzer {

    private final DataStore ds = DataStore.getInstance();

    /**
     * Analyzes expenses over the configured look-back window and returns one
     * {@link CashFlowProjectionService.ExpensePattern} per sub-category that
     * has at least 3 months of data.
     */
    List<CashFlowProjectionService.ExpensePattern> analyze() {
        int analysisMonths = ds.getExpenseForecastAnalysisMonths();
        LocalDate analysisEnd   = LocalDate.now();
        LocalDate analysisStart = analysisEnd.minusMonths(analysisMonths);

        Map<String, Map<YearMonth, Long>> subCategoryMonthlyExpenses = new HashMap<>();

        for (com.sanchay.model.Transaction t : ds.getTransactions()) {
            if (t.getType() != com.sanchay.model.Transaction.Type.EXPENSE) continue;
            if (t.getDate().isBefore(analysisStart) || t.getDate().isAfter(analysisEnd)) continue;
            if (t.getClassification() == null || t.getClassification().getCategoryId() == null) continue;
            if (t.getClassification().getSubCategoryId() == null) continue;

            String catId    = t.getClassification().getCategoryId();
            String subCatId = t.getClassification().getSubCategoryId();
            String key      = catId + "|" + subCatId;

            YearMonth ym = YearMonth.from(t.getDate());
            subCategoryMonthlyExpenses
                .computeIfAbsent(key, k -> new HashMap<>())
                .merge(ym, t.getAmountPaise(), Long::sum);
        }

        List<CashFlowProjectionService.ExpensePattern> patterns = new ArrayList<>();

        for (Map.Entry<String, Map<YearMonth, Long>> entry : subCategoryMonthlyExpenses.entrySet()) {
            String key   = entry.getKey();
            String[] parts = key.split("\\|");
            String categoryId    = parts[0];
            String subCategoryId = parts[1];
            Map<YearMonth, Long> monthlyData = entry.getValue();

            if (monthlyData.size() < 3) continue;

            long totalSpent = monthlyData.values().stream().mapToLong(Long::longValue).sum();
            //long avgMonthly = totalSpent / analysisMonths;
            long avgMonthly = totalSpent / Math.max(1, monthlyData.size());


            Map<Month, List<Long>> monthlyAmounts = new HashMap<>();
            for (Map.Entry<YearMonth, Long> e : monthlyData.entrySet()) {
                Month month = e.getKey().getMonth();
                monthlyAmounts.computeIfAbsent(month, k -> new ArrayList<>()).add(e.getValue());
            }

            Map<Month, Double> seasonalFactors = new HashMap<>();
            for (Month month : Month.values()) {
                List<Long> amounts = monthlyAmounts.get(month);
                if (amounts != null && !amounts.isEmpty()) {
                    double monthAvg = amounts.stream().mapToLong(Long::longValue).average().orElse(0.0);
                    seasonalFactors.put(month, avgMonthly > 0 ? monthAvg / avgMonthly : 1.0);
                } else {
                    seasonalFactors.put(month, 1.0);
                }
            }

            double trendSlope = calculateTrendSlope(monthlyData);
            patterns.add(new CashFlowProjectionService.ExpensePattern(
                    categoryId, subCategoryId, avgMonthly, seasonalFactors, trendSlope, monthlyData.size()));
        }

        return patterns;
    }

    private double calculateTrendSlope(Map<YearMonth, Long> monthlyData) {
        List<YearMonth> sortedMonths = monthlyData.keySet().stream().sorted().toList();
        if (sortedMonths.size() < 2) return 0.0;

        int n = sortedMonths.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;

        for (int i = 0; i < n; i++) {
            YearMonth ym = sortedMonths.get(i);
            double x = ym.getYear() * 12 + ym.getMonthValue();
            double y = monthlyData.get(ym);
            sumX  += x;
            sumY  += y;
            sumXY += x * y;
            sumXX += x * x;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        double avgY  = sumY / n;
        return avgY != 0 ? slope / avgY : 0.0;
    }
}

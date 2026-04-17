package com.sanchay.ui.reports;

import java.time.YearMonth;

record ForecastTableRow(
        String    month,
        String    category,
        String    subCategory,
        String    amount,
        long      amountPaise,
        String    method,
        boolean   excluded,
        String    categoryId,
        String    subCategoryId,
        YearMonth yearMonth
) {}

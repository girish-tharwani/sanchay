package com.sanchay.service;

import com.sanchay.model.MajorEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MajorEventPlannerTest {

    @Test
    void recurringForecastStartsFromFirstOccurrenceWhenStartDateIsPast() {
        MajorEvent event = new MajorEvent();
        event.setType(MajorEvent.EventType.RECURRING);
        event.setFrequency(MajorEvent.Frequency.MONTHLY);
        event.setAmountPaise(100_00);
        event.setStartDate("2026-01-01");

        MajorEventPlanner planner = new MajorEventPlanner();

        assertEquals(600_00, planner.computeEventForecast(event, LocalDate.of(2026, 6, 1)));
    }

    @Test
    void recurringPostRetirementForecastKeepsOriginalRecurrenceAnchor() {
        MajorEvent event = new MajorEvent();
        event.setType(MajorEvent.EventType.RECURRING);
        event.setFrequency(MajorEvent.Frequency.QUARTERLY);
        event.setAmountPaise(100_00);
        event.setStartDate("2026-01-15");

        MajorEventPlanner planner = new MajorEventPlanner();

        assertEquals(300_00, planner.computeEventPostRetirementForecast(
                event,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 11, 1)));
    }
}

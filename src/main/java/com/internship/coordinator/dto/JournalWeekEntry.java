package com.internship.coordinator.dto;

import java.util.List;

public record JournalWeekEntry(
        String weekStart,
        String weekEnd,
        List<JournalDayEntry> days,
        Boolean supervisorSignaturePresent) {}

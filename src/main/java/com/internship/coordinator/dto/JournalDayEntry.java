package com.internship.coordinator.dto;

import java.util.List;

public record JournalDayEntry(
        String date, String hoursFrom, String hoursTo, Integer workingHours, String activities) {}

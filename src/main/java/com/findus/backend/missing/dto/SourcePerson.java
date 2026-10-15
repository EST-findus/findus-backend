package com.findus.backend.missing.dto;

public record SourcePerson(String sourceId, String name, String category, String sex,
        String occurrenceDate, Integer age, Integer ageNow, String address,
        String clothing, String features, String photoUrl) {}

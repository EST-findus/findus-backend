package com.findus.backend.missing.dto;

public record SyncCounts(int received,int inserted,int updated,int unavailable,int imagesSaved,int photoFailures,SyncRequests requests) {
    public SyncCounts(int received,int inserted,int updated,int unavailable) {
        this(received,inserted,updated,unavailable,0,0,new SyncRequests(0,0,0));
    }
}

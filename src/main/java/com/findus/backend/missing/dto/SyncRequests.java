package com.findus.backend.missing.dto;
/** 재시도를 포함하여 실제 보낸 HTTP 요청 수입니다. */
public record SyncRequests(int api,int detail,int image,int total) {
    public SyncRequests(int api,int detail,int image) { this(api,detail,image,api+detail+image); }
}

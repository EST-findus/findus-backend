package com.findus.backend.news.entity;

import java.util.List;

public enum NewsPlacement {
    HOME, MYPAGE, BOTH;

    /** 공개 조회 위치에 함께 노출되는 값입니다. */
    public List<NewsPlacement> visibleWith() { return this==BOTH ? List.of(BOTH) : List.of(this,BOTH); }
}

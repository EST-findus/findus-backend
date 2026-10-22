package com.findus.backend.news.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.findus.backend.news.client.NaverNewsClient;
import com.findus.backend.news.config.NewsProperties;
import com.findus.backend.news.dto.NewsApproveRequest;
import com.findus.backend.news.dto.NewsArticleResponse;
import com.findus.backend.news.dto.NewsPageResponse;
import com.findus.backend.news.entity.NewsArticle;
import com.findus.backend.news.entity.NewsPlacement;
import com.findus.backend.news.exception.NewsException;
import com.findus.backend.news.repository.NewsArticleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;

@Service
public class NewsServiceImpl implements NewsService {
    private static final int PUBLIC_LIMIT=30;
    private final NaverNewsClient naver;
    private final NewsProperties properties;
    private final NewsArticleRepository articles;
    public NewsServiceImpl(NaverNewsClient naver,NewsProperties properties,NewsArticleRepository articles) {
        this.naver=naver;this.properties=properties;this.articles=articles;
    }

    @Override public JsonNode search(String query,int start,Integer display,String sort) {
        String q=query==null || query.isBlank() ? properties.defaultQuery() : query.trim();
        int size=display==null ? properties.display() : display;
        if (q.length()>500 || start<1 || start>1000 || size<1 || size>100 || !("date".equals(sort) || "sim".equals(sort)))
            throw invalid("검색어는 500자 이하, start는 1~1000, display는 1~100, sort는 date 또는 sim이어야 합니다.");
        return naver.search(q,start,size,sort);
    }

    @Override @Transactional public NewsArticleResponse approve(NewsApproveRequest r,UUID adminId) {
        String title=plain(r.title());
        if (title.isEmpty()) throw invalid("제목이 비어 있습니다.");
        String url=normalizeUrl(r.originalUrl());
        String naverUrl=r.naverUrl()==null || r.naverUrl().isBlank() ? null : normalizeUrl(r.naverUrl());
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime expiresAt=r.expiresAt()==null ? null : r.expiresAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        if (expiresAt!=null && !expiresAt.isAfter(now)) throw invalid("게시 종료일은 현재보다 이후여야 합니다.");
        // 동시에 같은 기사를 승인하면 부분 유니크 인덱스가 막고 ApiExceptionHandler가 409로 바꿉니다.
        if (articles.existsActive(url)) throw duplicate();
        String note=r.note()==null || r.note().isBlank() ? null : r.note().trim();
        var saved=articles.save(new NewsArticle(title,url,naverUrl,publishedAt(r.pubDate()),
                r.placement()==null ? NewsPlacement.BOTH : r.placement(),expiresAt,adminId,note));
        return NewsArticleResponse.admin(saved);
    }

    @Override @Transactional(readOnly=true) public NewsPageResponse approved(int page,int size) {
        if (page<0 || size<1 || size>100) throw invalid("page는 0 이상, size는 1~100이어야 합니다.");
        var result=articles.findActive(PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))));
        return new NewsPageResponse(result.map(NewsArticleResponse::admin).getContent(),page,size,result.getTotalElements(),result.getTotalPages());
    }

    @Override @Transactional public void withdraw(UUID id) {
        articles.findActive(id).orElseThrow(() -> new NewsException(HttpStatus.NOT_FOUND,"NEWS_NOT_FOUND","노출 중인 기사를 찾을 수 없습니다.")).softDelete();
    }

    @Override @Transactional(readOnly=true) public List<NewsArticleResponse> published(NewsPlacement placement) {
        if (placement==NewsPlacement.BOTH) throw invalid("placement는 HOME 또는 MYPAGE여야 합니다.");
        return articles.findPublic(placement,LocalDateTime.now(ZoneOffset.UTC),PUBLIC_LIMIT).stream().map(NewsArticleResponse::open).toList();
    }

    public static NewsException duplicate() { return new NewsException(HttpStatus.CONFLICT,"NEWS_ALREADY_APPROVED","이미 승인된 기사입니다."); }

    /** 네이버 제목의 <b> 강조 태그와 &quot; 같은 엔티티를 제거합니다. */
    static String plain(String value) {
        String text=HtmlUtils.htmlUnescape(value.replaceAll("<[^>]*>","")).replaceAll("\\s+"," ").trim();
        return text.length()>600 ? text.substring(0,600) : text;
    }

    /** 관리자가 보낸 주소는 신뢰 경계이므로 http/https 공개 주소 형태만 저장합니다. */
    static String normalizeUrl(String value) {
        try {
            URI uri=new URI(value.trim());
            String scheme=uri.getScheme()==null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost()==null || uri.getRawUserInfo()!=null)
                throw invalid("기사 주소는 http 또는 https 주소여야 합니다.");
            // 원문 인코딩(%26 등)을 바꾸지 않도록 raw 경로·쿼리를 그대로 이어 붙이고 fragment만 버립니다.
            String normalized=scheme+"://"+uri.getHost().toLowerCase(Locale.ROOT)+(uri.getPort()==-1 ? "" : ":"+uri.getPort())
                    +(uri.getRawPath()==null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                    +(uri.getRawQuery()==null ? "" : "?"+uri.getRawQuery());
            if (normalized.length()>2048) throw invalid("기사 주소가 너무 깁니다.");
            return normalized;
        } catch (URISyntaxException e) { throw invalid("기사 주소 형식을 확인해 주세요."); }
    }

    private static LocalDateTime publishedAt(String pubDate) {
        if (pubDate==null || pubDate.isBlank()) return null;
        try { return OffsetDateTime.parse(pubDate.trim(),DateTimeFormatter.RFC_1123_DATE_TIME).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(); }
        catch (DateTimeParseException e) { throw invalid("pubDate는 네이버 검색 결과 형식(RFC 1123)이어야 합니다."); }
    }
    private static NewsException invalid(String message) { return new NewsException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",message); }
}

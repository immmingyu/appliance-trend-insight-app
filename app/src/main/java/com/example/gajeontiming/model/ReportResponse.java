package com.example.gajeontiming.model;

import java.util.List;

/**
 * GET /api/report 응답 중 이 화면에서 쓰는 부분(뉴스)만 받는다.
 * 서버는 검색량(datalab), 유튜브, 커뮤니티 글도 주지만 Gson은 선언하지 않은 키를 그냥 건너뛴다.
 */
public class ReportResponse {
    public News news;

    public static class News {
        public List<NewsItem> items;
    }

    public static class NewsItem {
        public String title;
        public String description;
        public String pubDate;
        public String link;
    }
}

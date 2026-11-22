package com.example.gajeontiming.auth;

/** 회원 정보(유형 · 상태 · 등급)가 바뀌면 다시 그려야 하는 화면 (홈 · 마이페이지) */
public interface AccountAware {
    /** 메인 스레드에서 불린다 */
    void onAccountChanged();
}

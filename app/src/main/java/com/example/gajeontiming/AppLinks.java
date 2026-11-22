package com.example.gajeontiming;

/**
 * 마이페이지 "앱 정보" · 회원가입 약관 동의에서 여는 주소 · 연락처.
 * 출시 전에 모두 채워야 한다 (Play 스토어는 개인정보처리방침 · 계정 삭제 방법을 요구한다).
 * null이면 그 항목을 눌렀을 때 "준비 중이에요"라고만 안내한다.
 */
public final class AppLinks {
    /** 개인정보처리방침 웹페이지 (Play Console의 개인정보처리방침 URL과 같아야 한다) */
    public static final String PRIVACY_URL = "https://appliance-trend-insight.vercel.app/privacy";
    /** 이용약관 웹페이지 */
    public static final String TERMS_URL = "https://appliance-trend-insight.vercel.app/terms";
    /** 문의 받을 이메일 주소 */
    public static final String SUPPORT_EMAIL = "gajeonmussang@gmail.com";
    private AppLinks() { }
}

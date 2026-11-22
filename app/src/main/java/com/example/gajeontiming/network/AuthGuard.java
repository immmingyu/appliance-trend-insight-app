package com.example.gajeontiming.network;

import com.example.gajeontiming.auth.SessionManager;

import retrofit2.Response;

/**
 * 로그인 · 권한 오류를 한곳에서 해석한다.
 *   401 → 로그인 만료: 로그아웃 처리 (화면은 로그인 화면을 띄운다)
 *   403 → 권한 없음: 서버 문구, 없으면 "사업자 회원 전용 기능이에요"
 *   402 → 구독 등급 부족: 서버 문구만 보여 준다.
 *         Play 결제 정책 때문에 웹 결제로 안내하는 문구 · 링크는 넣지 않는다.
 *
 * 쓰는 법:
 *   if (AuthGuard.isLoggedOut(response)) { 로그인 화면 띄우기; return; }
 *   showError(AuthGuard.message(response, "불러오지 못했어요"));
 * (오류 본문은 한 번만 읽을 수 있어서 message는 응답당 한 번만 부른다)
 */
public final class AuthGuard {

    public static final String B2B_ONLY = "사업자 회원 전용 기능이에요.";
    public static final String TIER_REQUIRED = "이용 중인 구독 등급에서는 쓸 수 없는 기능이에요.";

    private AuthGuard() { }

    /** 401이면 저장된 로그인을 지우고 true */
    public static boolean isLoggedOut(Response<?> response) {
        if (response.code() != 401) return false;
        SessionManager.logout();
        return true;
    }

    /** 사용자에게 보여 줄 오류 문장 */
    public static String message(Response<?> response, String fallback) {
        switch (response.code()) {
            case 401:
                return "로그인이 만료됐어요. 다시 로그인해 주세요.";
            case 402: {
                String detail = ApiErrors.detail(response);
                return detail != null ? detail : TIER_REQUIRED;
            }
            case 403: {
                String detail = ApiErrors.detail(response);
                return detail != null ? detail : B2B_ONLY;
            }
            default:
                return ApiErrors.message(response, fallback);
        }
    }
}

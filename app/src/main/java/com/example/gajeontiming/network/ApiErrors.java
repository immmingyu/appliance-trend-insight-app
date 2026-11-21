package com.example.gajeontiming.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import retrofit2.Response;

/**
 * 서버 오류 응답에서 사용자에게 보여줄 문장을 꺼낸다.
 * 서버(FastAPI)는 오류를 {"detail": "이미 가입된 이메일입니다"} 형태로 돌려준다.
 */
public final class ApiErrors {

    private ApiErrors() { }

    public static String message(Response<?> response, String fallback) {
        try {
            if (response.errorBody() == null) return withCode(response, fallback);
            JsonObject obj = JsonParser.parseString(response.errorBody().string()).getAsJsonObject();
            JsonElement detail = obj.get("detail");
            // 입력 형식 오류(422)는 detail이 목록이라 문장이 아니다 → 기본 문구 사용
            if (detail != null && detail.isJsonPrimitive()) {
                return detail.getAsString();
            }
        } catch (Exception ignored) {
            // 서버가 JSON이 아닌 응답(점검 페이지 등)을 주면 기본 문구
        }
        return withCode(response, fallback);
    }

    /**
     * 기본 문구 뒤에 HTTP 코드를 붙인다 (예: "… (오류 500)").
     * 서버 안내가 없는 오류는 사용자 문의만으로 원인을 알기 어려워서, 코드만이라도 보이게 한다.
     */
    private static String withCode(Response<?> response, String fallback) {
        int code = response.code();
        return code >= 400 ? fallback + " (오류 " + code + ")" : fallback;
    }
}

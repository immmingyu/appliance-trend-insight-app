package com.example.gajeontiming.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * F-19 오프라인 캐시.
 * 공개 데이터 응답(CachePolicy)을 기기의 캐시 폴더에 저장해 두고,
 * 인터넷이 끊기거나 서버가 응답하지 못하면 저장된 응답을 대신 돌려준다.
 *
 *  - 저장: 서버 응답에 캐시 설정이 없어서 OkHttp가 저장하지 않으므로, 네트워크 인터셉터가
 *          "저장은 하되 매번 새로 받아라(max-age=0)"라고 붙여 준다 → 평소에는 항상 최신 데이터
 *  - 꺼내기: 요청이 실패(연결 오류) 또는 서버 오류(5xx)면 같은 요청을 "캐시에서만" 다시 시도한다
 *  - 안내: 저장된 결과를 보여줄 때 "○월 ○일 ○○:○○에 저장된 결과" 토스트를 띄운다 (20초에 한 번)
 *
 * 화면 코드는 바꿀 필요가 없다. 저장된 응답도 평소 응답과 똑같은 모양으로 화면에 전달된다.
 */
public final class OfflineCache {

    private static final long MAX_SIZE = 20L * 1024 * 1024;   // 20MB를 넘으면 오래된 것부터 지움
    private static final long NOTICE_INTERVAL_MS = 20_000;

    private static Context appContext;
    private static long lastNoticeAt = 0;

    private OfflineCache() { }

    public static Cache create(Context context) {
        appContext = context.getApplicationContext();
        return new Cache(new File(appContext.getCacheDir(), "http_offline"), MAX_SIZE);
    }

    /** 네트워크 인터셉터: 공개 데이터 응답을 저장 가능하게 표시 */
    public static Interceptor storeInterceptor() {
        return chain -> {
            Request request = chain.request();
            Response response = chain.proceed(request);
            if (response.isSuccessful() && CachePolicy.isCacheable(request.method(), request.url().encodedPath())) {
                return response.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, max-age=0")
                        .build();
            }
            return response;
        };
    }

    /** 애플리케이션 인터셉터: 실패하면 저장된 응답으로 대신한다 */
    public static Interceptor fallbackInterceptor() {
        return chain -> {
            Request request = chain.request();
            if (!CachePolicy.isCacheable(request.method(), request.url().encodedPath())) {
                return chain.proceed(request);
            }
            try {
                Response response = chain.proceed(request);
                if (response.code() < 500) return response;           // 정상 · 4xx는 그대로
                // 같은 요청을 다시 보내려면 앞 응답을 먼저 닫아야 한다 (안 닫으면 OkHttp가 예외 → 앱 종료).
                // 저장본이 없을 때 서버 안내(503 detail)를 그대로 돌려주려고 내용은 먼저 읽어 둔다
                Response error = copyAndClose(response);
                Response cached = fromCache(chain, request);           // 서버 오류면 저장본이 있는지
                return cached != null ? cached : error;
            } catch (IOException e) {
                if (chain.call().isCanceled()) throw e;               // 화면을 닫아 취소한 요청
                Response cached = fromCache(chain, request);
                if (cached == null) throw e;                           // 저장본도 없으면 원래 오류
                return cached;
            }
        };
    }

    /** 응답 내용을 메모리에 옮겨 담고 원래 응답은 닫는다 (오류 응답이라 내용이 작다) */
    private static Response copyAndClose(Response response) throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            response.close();
            return response;
        }
        MediaType type = body.contentType();
        byte[] bytes;
        try {
            bytes = body.bytes();   // 다 읽고 닫힌다
        } finally {
            response.close();
        }
        return response.newBuilder().body(ResponseBody.create(bytes, type)).build();
    }

    private static Response fromCache(Interceptor.Chain chain, Request request) throws IOException {
        Request onlyCache = request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build();
        Response cached = chain.proceed(onlyCache);
        if (!cached.isSuccessful()) {          // 저장본이 없으면 OkHttp가 504를 돌려준다
            cached.close();
            return null;
        }
        showNotice(cached.receivedResponseAtMillis());
        return cached;
    }

    /** 화면이 어디든 한 번 안내 (백그라운드 스레드에서 불리므로 메인 스레드로 넘긴다) */
    private static void showNotice(long savedAt) {
        if (appContext == null) return;
        long now = System.currentTimeMillis();
        if (now - lastNoticeAt < NOTICE_INTERVAL_MS) return;
        lastNoticeAt = now;
        String when = new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(new Date(savedAt));
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(appContext,
                "인터넷에 연결할 수 없어 " + when + "에 저장된 결과를 보여드려요", Toast.LENGTH_LONG).show());
    }
}

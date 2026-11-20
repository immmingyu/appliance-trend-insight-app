package com.example.gajeontiming.ui;

import android.provider.Settings;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

/**
 * 로딩 중인 뷰를 천천히 흐려졌다 진해지게 반복한다 (스켈레톤 · "답변 쓰는 중" 말풍선).
 * 뷰 애니메이션(AlphaAnimation)이라 뷰가 화면에서 사라지면 저절로 멈춘다 → 화면을 닫아도 누수가 없다.
 * 시스템 설정에서 애니메이션을 끈 사용자에게는 움직이지 않고 흐린 상태로만 보여 준다.
 */
public final class Pulse {
    private static final long DURATION_MS = 800;
    private static final float MIN_ALPHA = 0.4f;
    private static final float STILL_ALPHA = 0.6f;

    private Pulse() {}

    public static void start(View view) {
        view.clearAnimation();
        if (!animationsEnabled(view)) {
            view.setAlpha(STILL_ALPHA);
            return;
        }
        view.setAlpha(1f);
        AlphaAnimation anim = new AlphaAnimation(1f, MIN_ALPHA);
        anim.setDuration(DURATION_MS);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        view.startAnimation(anim);
    }

    /** 깜빡임을 멈추고 원래 진하기로 돌린다. 시작한 적 없는 뷰에 불러도 괜찮다 */
    public static void stop(View view) {
        view.clearAnimation();
        view.setAlpha(1f);
    }

    private static boolean animationsEnabled(View view) {
        float scale = Settings.Global.getFloat(view.getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        return scale > 0f;
    }
}

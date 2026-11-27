package com.example.gajeontiming.ui;

import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.example.gajeontiming.R;

/**
 * 제품 사진 표시. 저장된 정보(다나와 점검 중)는 image가 빈 문자열일 수 있어서
 * 빈 값 · 불러오기 실패면 기본 이미지를 보여 준다 (빈 칸 · 깨진 이미지 대신).
 */
public final class ProductImages {

    private ProductImages() { }

    public static void load(ImageView view, String url) {
        if (url == null || url.trim().isEmpty()) {
            Glide.with(view).clear(view);   // 재사용된 목록 칸에 이전 사진이 남지 않게
            view.setImageResource(R.drawable.ic_image_placeholder);
            return;
        }
        Glide.with(view).load(url).centerInside()
                .error(R.drawable.ic_image_placeholder)
                .into(view);
    }
}

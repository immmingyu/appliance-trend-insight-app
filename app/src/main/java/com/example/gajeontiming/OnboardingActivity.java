package com.example.gajeontiming;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

/**
 * 앱 소개 화면. 앱을 처음 설치하고 켰을 때만 시작 화면 다음에 나온다.
 * 옆으로 넘기는 3장 → 마지막 장의 "시작하기"(또는 "건너뛰기")를 누르면 홈으로 가고,
 * 다시는 나오지 않도록 기기에 기록한다.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final String PREF = "onboarding";
    private static final String KEY_DONE = "done";

    /** 소개 한 장의 내용. 그림은 임시로 이모지를 쓴다 */
    private static final class Page {
        final String image;
        final int title;
        final int desc;

        Page(String image, int title, int desc) {
            this.image = image;
            this.title = title;
            this.desc = desc;
        }
    }

    private static final Page[] PAGES = {
            new Page("🔍", R.string.onboarding_title_1, R.string.onboarding_desc_1),
            new Page("📉", R.string.onboarding_title_2, R.string.onboarding_desc_2),
            new Page("🔔", R.string.onboarding_title_3, R.string.onboarding_desc_3),
    };

    private ViewPager2 pager;
    private Button nextButton;
    private LinearLayout dots;

    /** 소개를 이미 봤는지 (SplashActivity에서 어디로 갈지 정할 때 쓴다) */
    public static boolean isDone(Context context) {
        return context.getSharedPreferences(PREF, MODE_PRIVATE).getBoolean(KEY_DONE, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        pager = findViewById(R.id.onboarding_pager);
        nextButton = findViewById(R.id.onboarding_next);
        dots = findViewById(R.id.onboarding_dots);

        pager.setAdapter(new PageAdapter());
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateBottom(position);
            }
        });

        findViewById(R.id.onboarding_skip).setOnClickListener(v -> finishOnboarding());
        nextButton.setOnClickListener(v -> {
            int current = pager.getCurrentItem();
            if (current < PAGES.length - 1) {
                pager.setCurrentItem(current + 1);   // 다음 장으로
            } else {
                finishOnboarding();                  // 마지막 장: 시작하기
            }
        });

        createDots();
        updateBottom(0);
    }

    // ── 아래쪽 점 · 버튼 ─────────────────────────────────────

    private void createDots() {
        int size = dp(8);
        int margin = dp(4);
        for (int i = 0; i < PAGES.length; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(margin, 0, margin, 0);
            dots.addView(dot, params);
        }
    }

    /** 지금 장의 점만 길쭉한 진한 보라로, 마지막 장이면 버튼을 "시작하기"로 */
    private void updateBottom(int position) {
        for (int i = 0; i < dots.getChildCount(); i++) {
            View dot = dots.getChildAt(i);
            boolean active = i == position;
            dot.setBackgroundResource(active ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
            ViewGroup.LayoutParams params = dot.getLayoutParams();
            params.width = active ? dp(20) : dp(8);
            dot.setLayoutParams(params);
        }
        nextButton.setText(position == PAGES.length - 1
                ? R.string.onboarding_start : R.string.onboarding_next);
    }

    private void finishOnboarding() {
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ── 넘기는 장 ────────────────────────────────────────────

    private static class PageAdapter extends RecyclerView.Adapter<PageAdapter.Holder> {

        static class Holder extends RecyclerView.ViewHolder {
            final TextView image;
            final TextView title;
            final TextView desc;

            Holder(View v) {
                super(v);
                image = v.findViewById(R.id.onboarding_image);
                title = v.findViewById(R.id.onboarding_title);
                desc = v.findViewById(R.id.onboarding_desc);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding_page, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Page page = PAGES[position];
            holder.image.setText(page.image);
            holder.title.setText(page.title);
            holder.desc.setText(page.desc);
        }

        @Override
        public int getItemCount() {
            return PAGES.length;
        }
    }
}

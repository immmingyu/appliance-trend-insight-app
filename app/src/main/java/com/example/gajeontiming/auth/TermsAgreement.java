package com.example.gajeontiming.auth;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.CheckBox;
import android.widget.Toast;

import com.example.gajeontiming.AppLinks;
import com.example.gajeontiming.R;

/**
 * 약관 동의 묶음(include_terms_agreement.xml)의 동작.
 * 전체 동의 ↔ 개별 동의를 서로 맞추고, "보기"로 약관 웹페이지를 연다.
 * 두 필수 항목이 모두 체크됐는지 바뀔 때마다 onChange로 알려 준다 (가입 버튼 켜고 끄기).
 * 회원가입(SignUpActivity) · 소셜 첫 가입(SocialSignupActivity)이 같이 쓴다.
 */
public final class TermsAgreement {

    private final Activity activity;
    private final CheckBox all, terms, privacy;
    private final Runnable onChange;
    private boolean syncing;   // 서로 맞추는 동안 리스너가 다시 불리지 않게

    public TermsAgreement(Activity activity, Runnable onChange) {
        this.activity = activity;
        this.onChange = onChange;
        all = activity.findViewById(R.id.terms_agree_all);
        terms = activity.findViewById(R.id.terms_agree_terms);
        privacy = activity.findViewById(R.id.terms_agree_privacy);

        all.setOnCheckedChangeListener((b, checked) -> {
            if (syncing) return;
            syncing = true;
            terms.setChecked(checked);
            privacy.setChecked(checked);
            syncing = false;
            onChange.run();
        });
        terms.setOnCheckedChangeListener((b, checked) -> onItemChanged());
        privacy.setOnCheckedChangeListener((b, checked) -> onItemChanged());

        activity.findViewById(R.id.terms_view_terms).setOnClickListener(v -> open(AppLinks.TERMS_URL));
        activity.findViewById(R.id.terms_view_privacy).setOnClickListener(v -> open(AppLinks.PRIVACY_URL));
    }

    /** 두 필수 항목(이용약관 · 개인정보 수집 · 이용)에 모두 동의했는지 */
    public boolean allAgreed() {
        return terms.isChecked() && privacy.isChecked();
    }

    private void onItemChanged() {
        if (syncing) return;
        syncing = true;
        all.setChecked(allAgreed());
        syncing = false;
        onChange.run();
    }

    private void open(String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "링크를 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show();
        }
    }
}

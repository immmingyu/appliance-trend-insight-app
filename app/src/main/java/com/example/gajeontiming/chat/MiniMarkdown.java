package com.example.gajeontiming.chat;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 답변(마크다운)을 TextView에 보여줄 글자로 바꾼다. 라이브러리 없이 자주 쓰는 것만 처리한다.
 *   # 제목 / ## 제목 / ### 제목  → 굵고 조금 크게
 *   - 항목 / * 항목              → • 항목
 *   **굵게**                     → 굵게
 *   `코드`                       → 기호만 지움
 *   표(| a | b |) · 구분선(---)  → 표는 칸을 " · "로 이어 한 줄로, 구분선은 지움
 */
public final class MiniMarkdown {

    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*)$");
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*+]\\s+(.*)$");
    private static final Pattern TABLE_RULE = Pattern.compile("^\\s*\\|?\\s*:?-{3,}.*$");
    private static final Pattern HR = Pattern.compile("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$");

    private MiniMarkdown() { }

    public static CharSequence render(String markdown) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (markdown == null) return out;

        String[] lines = markdown.replace("\r", "").split("\n");
        for (String raw : lines) {
            String line = raw.replace("`", "");
            if (HR.matcher(line).matches() || TABLE_RULE.matcher(line).matches()) continue;

            boolean heading = false;
            Matcher h = HEADING.matcher(line);
            Matcher b = BULLET.matcher(line);
            if (h.matches()) {
                line = h.group(1);
                heading = true;
            } else if (b.matches()) {
                line = "• " + b.group(1);
            } else if (line.trim().startsWith("|")) {
                // 표 한 줄: | 항목 | 값 | → 항목 · 값
                line = line.trim().replaceAll("^\\||\\|$", "").replaceAll("\\s*\\|\\s*", " · ").trim();
            }

            if (out.length() > 0) out.append("\n");
            int lineStart = out.length();
            appendWithBold(out, line);
            if (heading) {
                out.setSpan(new StyleSpan(Typeface.BOLD), lineStart, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(1.08f), lineStart, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        // 빈 줄이 3줄 넘게 이어지지 않게 정리
        String text = out.toString();
        while (text.contains("\n\n\n")) {
            int i = text.indexOf("\n\n\n");
            out.delete(i, i + 1);
            text = out.toString();
        }
        return out;
    }

    /** **굵게** 부분만 굵게 만들고 별표는 지운다 */
    private static void appendWithBold(SpannableStringBuilder out, String line) {
        Matcher m = BOLD.matcher(line);
        int last = 0;
        while (m.find()) {
            out.append(line, last, m.start());
            int s = out.length();
            out.append(m.group(1));
            out.setSpan(new StyleSpan(Typeface.BOLD), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            last = m.end();
        }
        out.append(line.substring(last));
    }
}

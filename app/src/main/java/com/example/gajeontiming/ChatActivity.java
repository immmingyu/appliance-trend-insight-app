package com.example.gajeontiming;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gajeontiming.chat.ChatAdapter;
import com.example.gajeontiming.chat.ChatMessage;
import com.example.gajeontiming.model.InsightModels;
import com.example.gajeontiming.network.ApiClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * F-17 AI 상담 화면 (로그인 불필요).
 * 질문을 보내면 서버가 실사용 후기 · 상품 데이터를 찾아 답한다.
 * 직전 대화 4개와 직전 답변의 조건(conditions)을 함께 보내서 "그럼 소음은?" 같은 이어지는 질문도 이해한다.
 * 다른 화면에서 EXTRA_QUESTION으로 질문을 넘기면 입력창에 미리 넣어 둔다.
 */
public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_QUESTION = "question";

    private static final String[] SUGGESTIONS = {
            "로봇청소기 실제 후기", "에어컨 여름 구매 타이밍", "비스포크 냉장고 장단점", "공기청정기 어떤 거 살까"
    };
    private static final int HISTORY_SIZE = 4;

    private RecyclerView list;
    private EditText input;
    private ImageButton sendButton;
    private View emptyBox;
    private ChatAdapter adapter;

    private JsonObject lastConditions;     // 직전 답변에서 서버가 뽑은 조건
    private Call<InsightModels.Response> call;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.chat_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        list = findViewById(R.id.chat_list);
        input = findViewById(R.id.chat_input);
        sendButton = findViewById(R.id.chat_send);
        emptyBox = findViewById(R.id.chat_empty);

        adapter = new ChatAdapter(this::showSources);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);          // 새 말이 아래에서 위로 쌓이게
        list.setLayoutManager(lm);
        list.setAdapter(adapter);

        // 처음 화면의 예시 질문
        ChipGroup suggestGroup = findViewById(R.id.chat_suggestions);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String s : SUGGESTIONS) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_filter_chip, suggestGroup, false);
            chip.setText(s);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> send(s));
            suggestGroup.addView(chip);
        }

        sendButton.setOnClickListener(v -> send(input.getText().toString()));
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                send(input.getText().toString());
                return true;
            }
            return false;
        });

        String preset = getIntent().getStringExtra(EXTRA_QUESTION);
        if (preset != null) {
            input.setText(preset);
            input.setSelection(preset.length());
        }
        updateEmpty();
    }

    // ── 질문 보내기 ───────────────────────────────────────────

    private void send(String raw) {
        String question = raw == null ? "" : raw.trim();
        if (question.isEmpty() || call != null) return;   // 빈 질문이거나 답을 기다리는 중이면 무시

        // 서버에 보낼 직전 대화 (지금 질문을 넣기 전의 마지막 4개)
        List<InsightModels.HistoryItem> history = new ArrayList<>();
        List<ChatMessage> all = adapter.getItems();
        for (int i = Math.max(0, all.size() - HISTORY_SIZE); i < all.size(); i++) {
            ChatMessage m = all.get(i);
            if (m.type == ChatMessage.TYPING) continue;
            history.add(new InsightModels.HistoryItem(m.type == ChatMessage.USER ? "user" : "ai", m.content));
        }

        adapter.add(new ChatMessage(ChatMessage.USER, question, null));
        adapter.add(new ChatMessage(ChatMessage.TYPING, "", null));
        input.setText("");
        setSending(true);
        updateEmpty();
        scrollToBottom();

        call = ApiClient.get().askInsight(new InsightModels.Request(question, lastConditions, history));
        call.enqueue(new Callback<InsightModels.Response>() {
            @Override
            public void onResponse(@NonNull Call<InsightModels.Response> c,
                                   @NonNull Response<InsightModels.Response> response) {
                call = null;
                adapter.removeTyping();
                setSending(false);
                InsightModels.Response body = response.body();
                if (!response.isSuccessful() || body == null || body.report == null) {
                    addAi(getString(R.string.chat_failed), null);
                    return;
                }
                if (body.conditions != null) lastConditions = body.conditions;
                addAi(body.report, body.sources);
            }

            @Override
            public void onFailure(@NonNull Call<InsightModels.Response> c, @NonNull Throwable t) {
                if (c.isCanceled()) return;
                call = null;
                adapter.removeTyping();
                setSending(false);
                addAi(getString(R.string.chat_network), null);
            }
        });
    }

    private void addAi(String text, List<InsightModels.Source> sources) {
        adapter.add(new ChatMessage(ChatMessage.AI, text, sources));
        scrollToBottom();
    }

    private void setSending(boolean sending) {
        sendButton.setEnabled(!sending);
        sendButton.setAlpha(sending ? 0.4f : 1f);
    }

    private void scrollToBottom() {
        list.post(() -> list.smoothScrollToPosition(Math.max(0, adapter.getItemCount() - 1)));
    }

    private void updateEmpty() {
        emptyBox.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    /** "참고 자료 N개"를 누르면 AI가 근거로 쓴 후기 · 상품 데이터를 보여준다 */
    private void showSources(ChatMessage m) {
        StringBuilder sb = new StringBuilder();
        for (InsightModels.Source s : m.sources) {
            if (sb.length() > 0) sb.append("\n\n");
            String text = s.text == null ? "" : s.text.trim();
            sb.append(s.rank).append(". ").append(text.length() > 300 ? text.substring(0, 300) + "…" : text);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.chat_sources_title)
                .setMessage(sb)
                .setPositiveButton("닫기", null)
                .show();
    }

    // ── 메뉴: 새 대화 ─────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.chat_new).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == 1) {
            if (call != null) {
                call.cancel();
                call = null;
                setSending(false);
            }
            adapter.clear();
            lastConditions = null;
            updateEmpty();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (call != null) call.cancel();
        super.onDestroy();
    }
}

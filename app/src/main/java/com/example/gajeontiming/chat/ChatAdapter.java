package com.example.gajeontiming.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gajeontiming.R;

import java.util.ArrayList;
import java.util.List;

/** 대화 목록. 내 질문은 오른쪽, AI 답변은 왼쪽 말풍선 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.Holder> {

    public interface OnSourcesClick {
        void onClick(ChatMessage message);
    }

    private final List<ChatMessage> items = new ArrayList<>();
    private final OnSourcesClick sourcesClick;

    public ChatAdapter(OnSourcesClick sourcesClick) {
        this.sourcesClick = sourcesClick;
    }

    public List<ChatMessage> getItems() {
        return items;
    }

    public void add(ChatMessage m) {
        items.add(m);
        notifyItemInserted(items.size() - 1);
    }

    /** 맨 끝의 "답변 쓰는 중"을 지운다 */
    public void removeTyping() {
        int last = items.size() - 1;
        if (last >= 0 && items.get(last).type == ChatMessage.TYPING) {
            items.remove(last);
            notifyItemRemoved(last);
        }
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == ChatMessage.USER ? R.layout.item_chat_user : R.layout.item_chat_ai;
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ChatMessage m = items.get(position);
        if (m.type == ChatMessage.USER) {
            h.text.setText(m.content);
            return;
        }
        if (m.type == ChatMessage.TYPING) {
            h.text.setText(R.string.chat_typing);
            h.text.setAlpha(0.6f);
        } else {
            h.text.setText(MiniMarkdown.render(m.content));
            h.text.setAlpha(1f);
        }
        if (h.sources != null) {
            boolean has = m.type == ChatMessage.AI && m.sources != null && !m.sources.isEmpty();
            h.sources.setVisibility(has ? View.VISIBLE : View.GONE);
            if (has) {
                h.sources.setText(h.sources.getContext()
                        .getString(R.string.chat_sources, m.sources.size()));
                h.sources.setOnClickListener(v -> sourcesClick.onClick(m));
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView text;
        final TextView sources;   // AI 말풍선에만 있음

        Holder(@NonNull View v) {
            super(v);
            text = v.findViewById(R.id.chat_text);
            sources = v.findViewById(R.id.chat_sources);
        }
    }
}

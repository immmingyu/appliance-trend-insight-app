package com.example.gajeontiming.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.gajeontiming.R;
import com.example.gajeontiming.model.RecommendResponse.RecommendItem;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** AI 추천 결과 카드: 순위 · 이미지 · 제품명 · 가격 · 핵심 장점 · 추천 이유 */
public class RecommendAdapter extends RecyclerView.Adapter<RecommendAdapter.Holder> {

    public interface OnItemClick {
        void onClick(RecommendItem item);
    }

    private final List<RecommendItem> items = new ArrayList<>();
    private final OnItemClick listener;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    public RecommendAdapter(OnItemClick listener) {
        this.listener = listener;
    }

    public void setItems(List<RecommendItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommend, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        RecommendItem it = items.get(position);
        h.rank.setText((position + 1) + "위");
        h.title.setText(it.title);
        h.price.setText(it.price > 0 ? won.format(it.price) + "원" : "가격 정보 없음");

        StringBuilder meta = new StringBuilder();
        if (it.reviewScore > 0) meta.append("★ ").append(it.reviewScore);
        if (it.reviewCount > 0) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append("리뷰 ").append(won.format(it.reviewCount));
        }
        setOrHide(h.meta, meta.toString());
        setOrHide(h.highlight, it.highlight);
        setOrHide(h.reason, it.reason);

        Glide.with(h.image).load(it.image).centerInside().into(h.image);
        h.itemView.setOnClickListener(v -> listener.onClick(it));
    }

    private void setOrHide(TextView v, String text) {
        if (text == null || text.trim().isEmpty()) {
            v.setVisibility(View.GONE);
        } else {
            v.setVisibility(View.VISIBLE);
            v.setText(text.trim());
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView rank, title, price, meta, highlight, reason;
        final ImageView image;

        Holder(@NonNull View v) {
            super(v);
            rank = v.findViewById(R.id.rec_rank);
            image = v.findViewById(R.id.rec_image);
            title = v.findViewById(R.id.rec_title);
            price = v.findViewById(R.id.rec_price);
            meta = v.findViewById(R.id.rec_meta);
            highlight = v.findViewById(R.id.rec_highlight);
            reason = v.findViewById(R.id.rec_reason);
        }
    }
}

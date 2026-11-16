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
import com.example.gajeontiming.model.TrendItem;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 홈 화면 트렌드 목록의 한 줄(item_trend.xml)에 데이터를 채운다. */
public class TrendAdapter extends RecyclerView.Adapter<TrendAdapter.Holder> {

    public interface OnItemClick {
        void onClick(TrendItem item);
    }

    private final List<TrendItem> items = new ArrayList<>();
    private final OnItemClick listener;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    public TrendAdapter(OnItemClick listener) {
        this.listener = listener;
    }

    public void setItems(List<TrendItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trend, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        TrendItem item = items.get(position);

        h.rank.setText(String.valueOf(position + 1));
        h.category.setText(item.category);
        h.title.setText(item.title);
        h.price.setText(item.price > 0 ? won.format(item.price) + "원" : "가격 정보 없음");

        // AI 이유와 태그는 서버의 AI 호출이 실패하면 비어서 온다 → 비면 숨김
        setOrHide(h.reason, item.reason);
        setOrHide(h.tag, item.tag);

        Glide.with(h.image).load(item.image).centerInside().into(h.image);

        h.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    private void setOrHide(TextView view, String text) {
        if (text == null || text.trim().isEmpty()) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(text);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView rank, category, title, price, reason, tag;
        final ImageView image;

        Holder(@NonNull View v) {
            super(v);
            rank = v.findViewById(R.id.trend_rank);
            image = v.findViewById(R.id.trend_image);
            category = v.findViewById(R.id.trend_category);
            title = v.findViewById(R.id.trend_title);
            price = v.findViewById(R.id.trend_price);
            reason = v.findViewById(R.id.trend_reason);
            tag = v.findViewById(R.id.trend_tag);
        }
    }
}

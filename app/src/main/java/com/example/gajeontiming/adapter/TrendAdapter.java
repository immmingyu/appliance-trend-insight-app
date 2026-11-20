package com.example.gajeontiming.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
        // 1~3위는 진한 보라 동그라미에 흰 글씨, 나머지는 연보라 동그라미에 보라 글씨
        boolean top3 = position < 3;
        h.rank.setBackgroundResource(top3 ? R.drawable.bg_rank_top : R.drawable.bg_rank);
        h.rank.setTextColor(top3 ? Color.WHITE
                : ContextCompat.getColor(h.rank.getContext(), R.color.brand_text));
        h.title.setText(item.title);
        h.price.setText(item.price > 0 ? won.format(item.price) + "원" : "가격 정보 없음");

        Glide.with(h.image).load(item.image).centerInside().into(h.image);

        h.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView rank, title, price;
        final ImageView image;

        Holder(@NonNull View v) {
            super(v);
            rank = v.findViewById(R.id.trend_rank);
            image = v.findViewById(R.id.trend_image);
            title = v.findViewById(R.id.trend_title);
            price = v.findViewById(R.id.trend_price);
        }
    }
}

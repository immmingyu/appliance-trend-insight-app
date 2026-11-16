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
import com.example.gajeontiming.data.RecentProductDb.Entry;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * F-08 최근 본 제품 목록.
 * compact=true  → 홈 화면의 가로 스크롤 작은 카드 (item_recent.xml)
 * compact=false → 마이페이지의 세로 목록 (item_product.xml 재사용)
 */
public class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.Holder> {

    public interface OnItemClick {
        void onClick(Entry entry);
    }

    private final List<Entry> items = new ArrayList<>();
    private final boolean compact;
    private final OnItemClick listener;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    public RecentAdapter(boolean compact, OnItemClick listener) {
        this.compact = compact;
        this.listener = listener;
    }

    public void setItems(List<Entry> entries) {
        items.clear();
        items.addAll(entries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = compact ? R.layout.item_recent : R.layout.item_product;
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.title.setText(e.product.title);
        h.price.setText(e.product.price > 0 ? won.format(e.product.price) + "원" : "가격 정보 없음");
        if (h.meta != null) {
            h.meta.setText("본 당시 가격" + (e.category != null ? " · " + e.category : ""));
        }
        Glide.with(h.image).load(e.product.image).centerInside().into(h.image);
        h.itemView.setOnClickListener(v -> listener.onClick(e));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title, price, meta;

        Holder(@NonNull View v) {
            super(v);
            // 두 레이아웃이 같은 id를 쓰고, 세로 목록에만 meta 줄이 있다
            image = v.findViewById(R.id.product_image);
            title = v.findViewById(R.id.product_title);
            price = v.findViewById(R.id.product_price);
            meta = v.findViewById(R.id.product_meta);
        }
    }
}

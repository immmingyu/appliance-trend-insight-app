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
import com.example.gajeontiming.model.Product;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 제품 목록 어댑터.
 * 줄 종류가 두 가지: 제품 한 줄(item_product.xml) / 맨 아래 "불러오는 중" 표시(item_loading.xml)
 */
public class ProductAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClick {
        void onClick(Product product);
    }

    private static final int TYPE_PRODUCT = 0;
    private static final int TYPE_LOADING = 1;

    private final List<Product> items = new ArrayList<>();
    private final OnItemClick listener;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);
    private boolean showFooter = false;

    public ProductAdapter(OnItemClick listener) {
        this.listener = listener;
    }

    public void clear() {
        items.clear();
        showFooter = false;
        notifyDataSetChanged();
    }

    public void addItems(List<Product> more) {
        int start = items.size();
        items.addAll(more);
        notifyItemRangeInserted(start, more.size());
    }

    public void setLoadingFooter(boolean show) {
        if (show == showFooter) return;
        showFooter = show;
        if (show) notifyItemInserted(items.size());
        else notifyItemRemoved(items.size());
    }

    /** 화면에 그리는 줄 수 (불러오는 중 표시 포함) */
    @Override
    public int getItemCount() {
        return items.size() + (showFooter ? 1 : 0);
    }

    /** 실제 제품 개수 */
    public int getProductCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return position < items.size() ? TYPE_PRODUCT : TYPE_LOADING;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_LOADING) {
            return new RecyclerView.ViewHolder(inf.inflate(R.layout.item_loading, parent, false)) { };
        }
        return new Holder(inf.inflate(R.layout.item_product, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (!(holder instanceof Holder)) return;
        Holder h = (Holder) holder;
        Product p = items.get(position);

        h.title.setText(p.title);
        h.price.setText(p.price > 0 ? won.format(p.price) + "원" : "가격 정보 없음");

        // 판매처 · 브랜드 · 리뷰 중 있는 것만 이어서 표시
        StringBuilder meta = new StringBuilder();
        append(meta, p.brand);
        append(meta, p.mallName);
        if (p.reviewCount > 0) {
            append(meta, "리뷰 " + won.format(p.reviewCount)
                    + (p.reviewScore > 0 ? " · ★" + p.reviewScore : ""));
        }
        h.meta.setText(meta);
        h.meta.setVisibility(meta.length() > 0 ? View.VISIBLE : View.GONE);

        Glide.with(h.image).load(p.image).centerInside().into(h.image);
        h.itemView.setOnClickListener(v -> listener.onClick(p));
    }

    private void append(StringBuilder sb, String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (sb.length() > 0) sb.append(" · ");
        sb.append(text.trim());
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title, price, meta;

        Holder(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.product_image);
            title = v.findViewById(R.id.product_title);
            price = v.findViewById(R.id.product_price);
            meta = v.findViewById(R.id.product_meta);
        }
    }
}

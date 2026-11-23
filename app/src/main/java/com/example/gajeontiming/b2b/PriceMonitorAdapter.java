package com.example.gajeontiming.b2b;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gajeontiming.R;
import com.example.gajeontiming.model.PriceMonitorResponse;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 가격 모니터 카테고리 목록 (item_price_monitor.xml) */
public class PriceMonitorAdapter extends RecyclerView.Adapter<PriceMonitorAdapter.Holder> {

    private final List<PriceMonitorResponse.Category> items = new ArrayList<>();
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    public void setItems(List<PriceMonitorResponse.Category> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_price_monitor, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        PriceMonitorResponse.Category c = items.get(position);
        Context ctx = h.itemView.getContext();

        h.category.setText(c.category);
        h.signal.setText(c.signal != null ? c.signal : "-");
        ViewCompat.setBackgroundTintList(h.signal,
                ColorStateList.valueOf(color(ctx, MonitorFormat.tone(c.signalType))));

        String price = ctx.getString(R.string.monitor_price, won.format(c.avgPrice), won.format(c.minPrice));
        if (c.realtime) price += " · " + ctx.getString(R.string.monitor_realtime);
        h.price.setText(price);

        bindChange(h.week, c.weekChangePct);
        bindChange(h.day, c.dayChangePct);

        if (c.brandChanges == null || c.brandChanges.isEmpty()) {
            h.brands.setVisibility(View.GONE);
        } else {
            StringBuilder sb = new StringBuilder(ctx.getString(R.string.monitor_brands));
            for (int i = 0; i < c.brandChanges.size(); i++) {
                PriceMonitorResponse.BrandChange b = c.brandChanges.get(i);
                sb.append(i == 0 ? " " : " · ").append(b.brand).append(' ').append(MonitorFormat.pct(b.changePct));
            }
            h.brands.setText(sb);
            h.brands.setVisibility(View.VISIBLE);
        }
    }

    private void bindChange(TextView view, Double value) {
        view.setText(MonitorFormat.pct(value));
        view.setTextColor(color(view.getContext(), MonitorFormat.changeTone(value)));
    }

    /** 색 이름은 score_*를 그대로 쓴다 (다크 모드 색도 이미 있음) */
    static int color(Context ctx, MonitorFormat.Tone tone) {
        switch (tone) {
            case DOWN: return ContextCompat.getColor(ctx, R.color.score_blue);
            case UP: return ContextCompat.getColor(ctx, R.color.score_red);
            default: return ContextCompat.getColor(ctx, R.color.monitor_neutral);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView category, signal, price, week, day, brands;

        Holder(@NonNull View v) {
            super(v);
            category = v.findViewById(R.id.monitor_category);
            signal = v.findViewById(R.id.monitor_signal);
            price = v.findViewById(R.id.monitor_price);
            week = v.findViewById(R.id.monitor_week);
            day = v.findViewById(R.id.monitor_day);
            brands = v.findViewById(R.id.monitor_brands);
        }
    }
}

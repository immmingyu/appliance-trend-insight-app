package com.example.gajeontiming.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gajeontiming.R;
import com.example.gajeontiming.model.AlertModels.Alert;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 가격 알림 목록. 지금 최저가는 화면이 따로 불러와서 setCurrentPrice로 채운다. */
public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.Holder> {

    public interface Listener {
        void onOpen(Alert alert);
        void onDelete(Alert alert);
    }

    private final List<Alert> items = new ArrayList<>();
    private final Map<Long, Long> nowPrices = new HashMap<>();   // alertId → 지금 최저가 (0이면 확인 실패)
    private final Listener listener;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    public AlertAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Alert> alerts) {
        items.clear();
        items.addAll(alerts);
        nowPrices.clear();
        notifyDataSetChanged();
    }

    public void remove(Alert alert) {
        int i = items.indexOf(alert);
        if (i >= 0) {
            items.remove(i);
            notifyItemRemoved(i);
        }
    }

    public void setCurrentPrice(long alertId, long price) {
        nowPrices.put(alertId, price);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).alertId == alertId) notifyItemChanged(i);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alert, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Alert a = items.get(position);
        long target = (long) a.targetPrice;

        h.name.setText(a.productName);
        h.target.setText("목표 " + won.format(target) + "원 이하");
        h.registered.setText("등록할 때 " + won.format((long) a.currentPrice) + "원");

        Long now = nowPrices.get(a.alertId);
        if (now == null) {
            h.now.setText("지금 가격 확인 중…");
            h.now.setTextColor(h.registered.getTextColors());
        } else if (now <= 0) {
            h.now.setText("지금 가격을 확인하지 못했어요");
            h.now.setTextColor(h.registered.getTextColors());
        } else if (now <= target) {
            h.now.setText("지금 " + won.format(now) + "원 · 목표 도달!");
            h.now.setTextColor(ContextCompat.getColor(h.now.getContext(), R.color.score_green));
        } else {
            h.now.setText("지금 " + won.format(now) + "원 · 목표까지 " + won.format(now - target) + "원");
            h.now.setTextColor(h.registered.getTextColors());
        }

        h.itemView.setOnClickListener(v -> listener.onOpen(a));
        h.delete.setOnClickListener(v -> listener.onDelete(a));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name, target, registered, now;
        final ImageButton delete;

        Holder(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.alert_name);
            target = v.findViewById(R.id.alert_target);
            registered = v.findViewById(R.id.alert_registered);
            now = v.findViewById(R.id.alert_now);
            delete = v.findViewById(R.id.alert_delete);
        }
    }
}

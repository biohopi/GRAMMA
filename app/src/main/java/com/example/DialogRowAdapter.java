package com.example;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DialogRowAdapter extends RecyclerView.Adapter<DialogRowAdapter.ViewHolder> {

    public interface OnRowActionListener {
        void onRowClick(DialogRowItem item);
        void onRowActionClick(DialogRowItem item);
    }

    public static class DialogRowItem {
        public final int id; // 0 for bookmarks
        public final String title;
        public final String subtitle;
        public final boolean isBookmark;

        public DialogRowItem(int id, String title, String subtitle, boolean isBookmark) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.isBookmark = isBookmark;
        }
    }

    private final List<DialogRowItem> items = new ArrayList<>();
    private final OnRowActionListener listener;

    public DialogRowAdapter(OnRowActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<DialogRowItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DialogRowItem item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imgIcon;
        private final TextView tvTitle;
        private final TextView tvSubtitle;
        private final ImageButton btnAction;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.img_row_icon);
            tvTitle = itemView.findViewById(R.id.tv_row_title);
            tvSubtitle = itemView.findViewById(R.id.tv_row_subtitle);
            btnAction = itemView.findViewById(R.id.btn_row_action);
        }

        public void bind(DialogRowItem item, OnRowActionListener listener) {
            tvTitle.setText(item.title);
            tvSubtitle.setText(item.subtitle);

            if (item.isBookmark) {
                imgIcon.setImageResource(android.R.drawable.btn_star_big_on);
                btnAction.setImageResource(android.R.drawable.ic_menu_delete);
            } else {
                imgIcon.setImageResource(android.R.drawable.ic_menu_recent_history);
                btnAction.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            }

            itemView.setOnClickListener(v -> listener.onRowClick(item));
            btnAction.setOnClickListener(v -> listener.onRowActionClick(item));
        }
    }
}

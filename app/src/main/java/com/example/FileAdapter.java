package com.example;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.FileItem;

import java.util.ArrayList;
import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(FileItem item);
        void onItemMenuClick(FileItem item, View anchorView);
    }

    private final List<FileItem> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public FileAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<FileItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imgIcon;
        private final TextView tvName;
        private final TextView tvInfo;
        private final ImageButton btnMenu;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.img_file_icon);
            tvName = itemView.findViewById(R.id.tv_file_name);
            tvInfo = itemView.findViewById(R.id.tv_file_info);
            btnMenu = itemView.findViewById(R.id.btn_item_menu);
        }

        public void bind(FileItem item, OnItemClickListener listener) {
            tvName.setText(item.getName());
            
            if (item.isDirectory()) {
                imgIcon.setImageResource(android.R.drawable.ic_menu_save); // Folder representation
                tvInfo.setText("Directory • " + item.getFormattedDate());
            } else {
                imgIcon.setImageResource(android.R.drawable.ic_menu_agenda); // File representation
                tvInfo.setText(item.getFormattedSize() + " • " + item.getFormattedDate());
            }

            itemView.setOnClickListener(v -> listener.onItemClick(item));
            btnMenu.setOnClickListener(v -> listener.onItemMenuClick(item, btnMenu));
        }
    }
}

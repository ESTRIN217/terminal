package com.termux.app.activities;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.ViewHolder> {

    private final List<File> mFiles;
    private final OnFileClickListener mListener;
    private final SimpleDateFormat mDateFormat;

    public interface OnFileClickListener {
        void onFileClick(File file);
    }

    public FileListAdapter(List<File> files, OnFileClickListener listener) {
        this.mFiles = files;
        this.mListener = listener;
        this.mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    }

    public void updateFiles(List<File> files) {
        mFiles.clear();
        mFiles.addAll(files);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_file_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = mFiles.get(position);
        holder.bind(file);
    }

    @Override
    public int getItemCount() {
        return mFiles.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView iconView;
        final TextView nameView;
        final TextView sizeView;
        final TextView dateView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.file_icon);
            nameView = itemView.findViewById(R.id.file_name);
            sizeView = itemView.findViewById(R.id.file_size);
            dateView = itemView.findViewById(R.id.file_date);
        }

        void bind(File file) {
            nameView.setText(file.getName());
            dateView.setText(mDateFormat.format(new Date(file.lastModified())));

            if (file.isDirectory()) {
                iconView.setImageResource(R.drawable.ic_folder);
                sizeView.setText("");
            } else {
                iconView.setImageResource(R.drawable.ic_file);
                sizeView.setText(formatSize(file.length()));
            }

            itemView.setOnClickListener(v -> {
                if (mListener != null) mListener.onFileClick(file);
            });
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        if (exp > 6) exp = 6;
        String pre = "KMGTPE".charAt(exp - 1) + "iB";
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024, exp), pre);
    }
}

package com.termux.app.activities;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.filemanager.FileOperationsHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.ViewHolder> {

    private List<File> mFiles;
    private final OnFileClickListener mListener;
    private final OnFileLongClickListener mLongListener;
    private final SimpleDateFormat mDateFormat;
    private boolean mSelectionMode;
    private final Set<File> mSelectedFiles;
    private boolean mShowHidden;

    public interface OnFileClickListener {
        void onFileClick(File file);
    }

    public interface OnFileLongClickListener {
        boolean onFileLongClick(File file, View view);
    }

    public FileListAdapter(List<File> files, OnFileClickListener listener, OnFileLongClickListener longListener) {
        this.mFiles = files;
        this.mListener = listener;
        this.mLongListener = longListener;
        this.mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        this.mSelectedFiles = new HashSet<>();
    }

    public void updateFiles(List<File> files) {
        mFiles = files;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean selectionMode) {
        mSelectionMode = selectionMode;
        if (!selectionMode) {
            mSelectedFiles.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return mSelectionMode;
    }

    public Set<File> getSelectedFiles() {
        return mSelectedFiles;
    }

    public void selectAll() {
        mSelectedFiles.clear();
        mSelectedFiles.addAll(mFiles);
        notifyDataSetChanged();
    }

    public void deselectAll() {
        mSelectedFiles.clear();
        notifyDataSetChanged();
    }

    public boolean isAllSelected() {
        return mSelectedFiles.size() == mFiles.size();
    }

    public int getSelectedCount() {
        return mSelectedFiles.size();
    }

    public void toggleFileSelection(File file) {
        if (mSelectedFiles.contains(file)) {
            mSelectedFiles.remove(file);
        } else {
            mSelectedFiles.add(file);
        }
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
        final CheckBox checkbox;
        final ImageView iconView;
        final TextView nameView;
        final TextView sizeView;
        final TextView dateView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox = itemView.findViewById(R.id.file_checkbox);
            iconView = itemView.findViewById(R.id.file_icon);
            nameView = itemView.findViewById(R.id.file_name);
            sizeView = itemView.findViewById(R.id.file_size);
            dateView = itemView.findViewById(R.id.file_date);
        }

        void bind(File file) {
            int iconRes = getIconForFile(file);
            iconView.setImageResource(iconRes);
            nameView.setText(file.getName());
            dateView.setText(mDateFormat.format(new Date(file.lastModified())));

            if (file.isDirectory()) {
                sizeView.setText("");
            } else {
                sizeView.setText(FileOperationsHelper.formatSize(file.length()));
            }

            checkbox.setVisibility(mSelectionMode ? View.VISIBLE : View.GONE);
            checkbox.setChecked(mSelectedFiles.contains(file));

            itemView.setActivated(mSelectedFiles.contains(file));
            int alpha = mSelectedFiles.contains(file) ? 77 : 0;
            itemView.setBackgroundColor(alpha << 24);

            itemView.setOnClickListener(v -> {
                if (mSelectionMode) {
                    toggleFileSelection(file);
                } else if (mListener != null) {
                    mListener.onFileClick(file);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (mLongListener != null) {
                    return mLongListener.onFileLongClick(file, itemView);
                }
                return false;
            });
        }
    }

    private int getIconForFile(File file) {
        if (file.isDirectory()) return R.drawable.ic_folder;
        String name = file.getName().toLowerCase();
        if (name.endsWith(".sh") || name.endsWith(".py") || name.endsWith(".pl") ||
            name.endsWith(".rb") || name.endsWith(".js")) return R.drawable.ic_file;
        if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".log") ||
            name.endsWith(".xml") || name.endsWith(".json") || name.endsWith(".yml") ||
            name.endsWith(".yaml") || name.endsWith(".conf") || name.endsWith(".properties"))
            return R.drawable.ic_file;
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
            name.endsWith(".gif") || name.endsWith(".webp")) return R.drawable.ic_file;
        if (name.endsWith(".zip") || name.endsWith(".tar") || name.endsWith(".gz") ||
            name.endsWith(".rar")) return R.drawable.ic_file;
        if (name.endsWith(".html") || name.endsWith(".htm")) return R.drawable.ic_file;
        if (name.endsWith(".pdf")) return R.drawable.ic_file;
        if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac"))
            return R.drawable.ic_file;
        if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi"))
            return R.drawable.ic_file;
        return R.drawable.ic_file;
    }
}

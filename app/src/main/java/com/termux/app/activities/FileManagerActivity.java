package com.termux.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.theme.NightMode;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileManagerActivity extends AppCompatActivity {

    private static final String LOG_TAG = "FileManagerActivity";

    private RecyclerView mFileList;
    private TextView mCurrentPath;
    private FileListAdapter mAdapter;
    private List<File> mFiles;
    private File mCurrentDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        setContentView(R.layout.activity_file_manager);

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);

        mFileList = findViewById(R.id.file_list);
        mCurrentPath = findViewById(R.id.current_path);

        mFiles = new ArrayList<>();
        mAdapter = new FileListAdapter(mFiles, file -> {
            if (file.isDirectory()) {
                navigateTo(file);
            } else {
                openFile(file);
            }
        });

        mFileList.setLayoutManager(new LinearLayoutManager(this));
        mFileList.setAdapter(mAdapter);

        File homeDir = Environment.getExternalStorageDirectory();
        File termuxHome = new File("/data/data/com.termux/files/home");
        if (termuxHome.exists()) {
            homeDir = termuxHome;
        }
        navigateTo(homeDir);
    }

    @Override
    public boolean onSupportNavigateUp() {
        goUp();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!goUp()) {
            super.onBackPressed();
        }
    }

    private boolean goUp() {
        File parent = mCurrentDir.getParentFile();
        if (parent != null && parent.exists()) {
            navigateTo(parent);
            return true;
        }
        return false;
    }

    private void navigateTo(File dir) {
        if (!dir.isDirectory() || !dir.exists()) return;

        mCurrentDir = dir;
        updateTitle(dir);
        mCurrentPath.setText(dir.getAbsolutePath());

        File[] files = dir.listFiles(file -> !file.getName().startsWith("."));
        mFiles.clear();

        if (files != null) {
            List<File> fileList = new ArrayList<>(Arrays.asList(files));
            Collections.sort(fileList, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            mFiles.addAll(fileList);
        }

        mAdapter.notifyDataSetChanged();
    }

    private void updateTitle(File dir) {
        String name = dir.getName();
        if (dir.getAbsolutePath().equals("/")) {
            setTitle("/");
        } else if ("home".equals(name) && dir.getParent() != null && dir.getParent().endsWith("/files")) {
            setTitle("~");
        } else {
            setTitle(name);
        }
    }

    private void openFile(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", file);
            String mimeType = getMimeType(file.getName());
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.action_open_file)));
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to open file: " + file.getAbsolutePath(), e);
        }
    }

    private String getMimeType(String fileName) {
        String ext = fileName.contains(".") ?
            fileName.substring(fileName.lastIndexOf('.')).toLowerCase() : "";
        switch (ext) {
            case ".txt":
            case ".md":
            case ".log":
            case ".sh":
            case ".py":
            case ".java":
            case ".xml":
            case ".json":
            case ".yml":
            case ".yaml":
            case ".conf":
            case ".cfg":
            case ".ini":
            case ".properties":
                return "text/plain";
            case ".png":
                return "image/png";
            case ".jpg":
            case ".jpeg":
                return "image/jpeg";
            case ".gif":
                return "image/gif";
            case ".webp":
                return "image/webp";
            case ".pdf":
                return "application/pdf";
            case ".html":
            case ".htm":
                return "text/html";
            case ".zip":
                return "application/zip";
            case ".tar":
            case ".gz":
                return "application/gzip";
            case ".mp3":
                return "audio/mpeg";
            case ".mp4":
                return "video/mp4";
            default:
                return "*/*";
        }
    }
}

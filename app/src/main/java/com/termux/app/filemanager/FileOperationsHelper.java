package com.termux.app.filemanager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.termux.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileOperationsHelper {

    private static final String LOG_TAG = "FileOperationsHelper";
    private static List<File> sClipboardFiles = new ArrayList<>();
    private static ClipboardOperation sClipboardOperation = ClipboardOperation.NONE;

    public enum ClipboardOperation {
        NONE, COPY, CUT
    }

    public static List<File> getClipboardFiles() {
        return sClipboardFiles;
    }

    public static ClipboardOperation getClipboardOperation() {
        return sClipboardOperation;
    }

    public static boolean hasClipboard() {
        return sClipboardOperation != ClipboardOperation.NONE && !sClipboardFiles.isEmpty();
    }

    public static void setClipboard(List<File> files, ClipboardOperation operation) {
        sClipboardFiles = new ArrayList<>(files);
        sClipboardOperation = operation;
    }

    public static void clearClipboard() {
        sClipboardFiles.clear();
        sClipboardOperation = ClipboardOperation.NONE;
    }

    public static boolean createFile(File parent, String name) {
        File file = new File(parent, name);
        try {
            return file.createNewFile();
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean createDirectory(File parent, String name) {
        File dir = new File(parent, name);
        return dir.mkdir();
    }

    public static boolean renameFile(File file, String newName) {
        File dest = new File(file.getParent(), newName);
        return file.renameTo(dest);
    }

    public static boolean deleteFile(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteFile(child)) return false;
                }
            }
        }
        return file.delete();
    }

    public static boolean copyFile(File source, File dest) {
        if (source.isDirectory()) {
            if (!dest.mkdirs()) return false;
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!copyFile(child, new File(dest, child.getName()))) return false;
                }
            }
            return true;
        } else {
            try {
                if (!dest.getParentFile().exists()) dest.getParentFile().mkdirs();
                try (InputStream in = new FileInputStream(source);
                     OutputStream out = new FileOutputStream(dest)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }

    public static boolean moveFile(File source, File dest) {
        if (source.renameTo(dest)) return true;
        if (copyFile(source, dest)) {
            deleteFile(source);
            return true;
        }
        return false;
    }

    public static String getFileDetails(Context context, File file) {
        StringBuilder details = new StringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        details.append(context.getString(R.string.detail_name)).append(": ").append(file.getName()).append("\n");
        details.append(context.getString(R.string.detail_path)).append(": ").append(file.getAbsolutePath()).append("\n");
        details.append(context.getString(R.string.detail_size)).append(": ").append(formatSize(file.length())).append("\n");
        details.append(context.getString(R.string.detail_modified)).append(": ").append(dateFormat.format(new Date(file.lastModified()))).append("\n");

        String perms = getPermissions(file);
        details.append(context.getString(R.string.detail_permissions)).append(": ").append(perms).append("\n");

        String mimeType = getMimeType(file.getName());
        details.append(context.getString(R.string.detail_mime)).append(": ").append(mimeType).append("\n");

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            int count = children != null ? children.length : 0;
            details.append(context.getString(R.string.detail_contents)).append(": ").append(count).append(" ").append(context.getString(R.string.detail_items));
        }

        return details.toString();
    }

    private static String getPermissions(File file) {
        StringBuilder perms = new StringBuilder();
        perms.append(file.isDirectory() ? "d" : "-");
        perms.append(file.canRead() ? "r" : "-");
        perms.append(file.canWrite() ? "w" : "-");
        perms.append(file.canExecute() ? "x" : "-");
        perms.append("---");
        perms.append("---");
        return perms.toString();
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        if (exp > 6) exp = 6;
        String pre = "KMGTPE".charAt(exp - 1) + "iB";
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024, exp), pre);
    }

    public static String getMimeType(String fileName) {
        String ext = fileName.contains(".") ?
            fileName.substring(fileName.lastIndexOf('.')).toLowerCase() : "";
        switch (ext) {
            case ".txt": case ".md": case ".log": case ".sh": case ".py":
            case ".java": case ".xml": case ".json": case ".yml": case ".yaml":
            case ".conf": case ".cfg": case ".ini": case ".properties":
                return "text/plain";
            case ".png": return "image/png";
            case ".jpg": case ".jpeg": return "image/jpeg";
            case ".gif": return "image/gif";
            case ".webp": return "image/webp";
            case ".pdf": return "application/pdf";
            case ".html": case ".htm": return "text/html";
            case ".zip": return "application/zip";
            case ".tar": case ".gz": return "application/gzip";
            case ".mp3": return "audio/mpeg";
            case ".mp4": return "video/mp4";
            default: return "*/*";
        }
    }

    public static void shareFiles(Context context, List<File> files) {
        if (files.isEmpty()) return;
        Intent intent;
        if (files.size() == 1) {
            intent = new Intent(Intent.ACTION_SEND);
            Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", files.get(0));
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setType(getMimeType(files.get(0).getName()));
        } else {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            ArrayList<Uri> uris = new ArrayList<>();
            String mimeType = "*/*";
            for (File f : files) {
                Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", f);
                uris.add(uri);
                String mt = getMimeType(f.getName());
                if (!mt.equals("*/*")) mimeType = mt;
            }
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.setType(mimeType);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)));
    }
}

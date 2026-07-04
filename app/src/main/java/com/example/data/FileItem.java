package com.example.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileItem {
    private final String name;
    private final String path;
    private final long size;
    private final long lastModified; // UNIX timestamp in seconds
    private final boolean isDirectory;

    public FileItem(String name, String path, long size, long lastModified, boolean isDirectory) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.lastModified = lastModified;
        this.isDirectory = isDirectory;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public String getFormattedSize() {
        if (isDirectory) {
            return "--";
        } else {
            double kb = size / 1024.0;
            double mb = kb / 1024.0;
            double gb = mb / 1024.0;
            if (gb >= 1.0) {
                return String.format(Locale.getDefault(), "%.2f GB", gb);
            } else if (mb >= 1.0) {
                return String.format(Locale.getDefault(), "%.2f MB", mb);
            } else if (kb >= 1.0) {
                return String.format(Locale.getDefault(), "%.2f KB", kb);
            } else {
                return size + " B";
            }
        }
    }

    public String getFormattedDate() {
        if (lastModified <= 0) return "--";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(lastModified * 1000));
    }
}

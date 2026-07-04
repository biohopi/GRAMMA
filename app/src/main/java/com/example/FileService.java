package com.example;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileService extends IFileService.Stub {
    private static final String TAG = "FileService";
    private Context context;

    public FileService() {
        Log.d(TAG, "FileService constructor called");
    }

    // Shizuku v11+ supports taking Context in the constructor to load app components
    public FileService(Context context) {
        this.context = context;
        Log.d(TAG, "FileService constructor called with Context");
    }

    @Override
    public List<String> listFiles(String dirPath) {
        List<String> result = new ArrayList<>();
        try {
            File dir = new File(dirPath);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    long size = f.isFile() ? f.length() : 0;
                    long mtime = f.lastModified() / 1000; // standard epoch seconds
                    String type = f.isDirectory() ? "dir" : "file";
                    result.add(name + "|" + size + "|" + mtime + "|" + type);
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "listFiles failed for " + dirPath, e);
        }
        return result;
    }

    @Override
    public String readFile(String filePath) {
        try {
            File file = new File(filePath);
            byte[] bytes = new byte[(int) file.length()];
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                int bytesRead = 0;
                while (bytesRead < bytes.length) {
                    int read = fis.read(bytes, bytesRead, bytes.length - bytesRead);
                    if (read == -1) break;
                    bytesRead += read;
                }
            }
            return new String(bytes, "UTF-8");
        } catch (Throwable e) {
            Log.e(TAG, "readFile failed for " + filePath, e);
            return "ERROR: " + e.getMessage();
        }
    }

    @Override
    public boolean writeFile(String filePath, String content) {
        try {
            File file = new File(filePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                fos.write(content.getBytes("UTF-8"));
            }
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "writeFile failed for " + filePath, e);
            return false;
        }
    }

    @Override
    public boolean createFolder(String parentPath, String folderName) {
        try {
            File dir = new File(parentPath, folderName);
            return dir.mkdirs();
        } catch (Throwable e) {
            Log.e(TAG, "createFolder failed for " + folderName + " in " + parentPath, e);
            return false;
        }
    }

    @Override
    public boolean createFile(String parentPath, String fileName) {
        try {
            File file = new File(parentPath, fileName);
            if (file.exists()) return true;
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            return file.createNewFile();
        } catch (Throwable e) {
            Log.e(TAG, "createFile failed for " + fileName + " in " + parentPath, e);
            return false;
        }
    }

    @Override
    public boolean deletePath(String path) {
        try {
            File file = new File(path);
            return deleteRecursively(file);
        } catch (Throwable e) {
            Log.e(TAG, "deletePath failed for " + path, e);
            return false;
        }
    }

    private boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }

    @Override
    public boolean renamePath(String oldPath, String newPath) {
        try {
            File oldFile = new File(oldPath);
            File newFile = new File(newPath);
            File parent = newFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            return oldFile.renameTo(newFile);
        } catch (Throwable e) {
            Log.e(TAG, "renamePath failed from " + oldPath + " to " + newPath, e);
            return false;
        }
    }

    @Override
    public boolean copyFile(String srcPath, String destPath) {
        try {
            File srcFile = new File(srcPath);
            File destFile = new File(destPath);
            File parent = destFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (java.io.FileInputStream fis = new java.io.FileInputStream(srcFile);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
            }
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "copyFile failed from " + srcPath + " to " + destPath, e);
            return false;
        }
    }

    @Override
    public void destroy() {
        Log.d(TAG, "destroy called, exiting process");
        System.exit(0);
    }
}

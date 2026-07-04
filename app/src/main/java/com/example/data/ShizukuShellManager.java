package com.example.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import com.example.FileService;
import com.example.IFileService;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;
import android.os.RemoteException;
import com.example.IPackageManager;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ShizukuShellManager {
    private static final String TAG = "ShizukuShell";

    private static final IPackageManager PACKAGE_MANAGER = IPackageManager.Stub.asInterface(
            new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")));

    public static void grantRuntimePermission(String packageName, String permissionName, int userId) {
        try {
            PACKAGE_MANAGER.grantRuntimePermission(packageName, permissionName, userId);
        } catch (RemoteException tr) {
            throw new RuntimeException(tr.getMessage(), tr);
        }
    }

    private static IFileService fileService = null;
    private static boolean isBinding = false;
    private static final List<Runnable> postBindQueue = new ArrayList<>();

    public interface OnBinderStateChangeListener {
        void onStateChanged(boolean bound);
    }

    private static final List<OnBinderStateChangeListener> listeners = new ArrayList<>();

    public static void addListener(OnBinderStateChangeListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public static void removeListener(OnBinderStateChangeListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private static void notifyStateChanged(boolean bound) {
        List<OnBinderStateChangeListener> targets;
        synchronized (listeners) {
            targets = new ArrayList<>(listeners);
        }
        for (OnBinderStateChangeListener l : targets) {
            try {
                l.onStateChanged(bound);
            } catch (Throwable ignored) {}
        }
    }

    private static final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "FileService bound successfully!");
            fileService = IFileService.Stub.asInterface(binder);
            isBinding = false;

            List<Runnable> runnables;
            synchronized (postBindQueue) {
                runnables = new ArrayList<>(postBindQueue);
                postBindQueue.clear();
            }
            for (Runnable r : runnables) {
                try {
                    r.run();
                } catch (Throwable ignored) {}
            }
            notifyStateChanged(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "FileService disconnected.");
            fileService = null;
            isBinding = false;
            notifyStateChanged(false);
        }
    };

    public static boolean isShizukuRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean hasShizukuPermission() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }

    public static int getShizukuUid() {
        try {
            if (isShizukuRunning()) {
                return Shizuku.getUid();
            }
        } catch (Throwable e) {
            Log.e(TAG, "Failed to get Shizuku UID", e);
        }
        return -1;
    }

    public static int checkShizukuPermission(String permission, int pid, int uid) {
        if (uid == 0) {
            return PackageManager.PERMISSION_GRANTED; // Root has all permissions
        }
        if (uid == 2000) {
            // ADB has typical shell developer permissions
            if ("android.permission.GRANT_RUNTIME_PERMISSIONS".equals(permission) ||
                "android.permission.INSTALL_PACKAGES".equals(permission) ||
                "android.permission.INTERACT_ACROSS_USERS_FULL".equals(permission) ||
                "android.permission.DUMP".equals(permission)) {
                return PackageManager.PERMISSION_GRANTED;
            }
        }
        return PackageManager.PERMISSION_DENIED;
    }

    public static boolean checkShizukuPermission(String permission) {
        int uid = getShizukuUid();
        return checkShizukuPermission(permission, -1, uid) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isServiceBound() {
        return fileService != null;
    }

    public static void bindService(Context context, Runnable onBound) {
        if (fileService != null) {
            if (onBound != null) onBound.run();
            return;
        }

        if (onBound != null) {
            synchronized (postBindQueue) {
                postBindQueue.add(onBound);
            }
        }

        if (isBinding) {
            return;
        }

        if (!isShizukuRunning() || !hasShizukuPermission()) {
            Log.w(TAG, "Cannot bind UserService: Shizuku not running or no permission");
            return;
        }

        isBinding = true;
        Log.d(TAG, "Binding FileService via Shizuku...");

        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(context.getPackageName(), FileService.class.getName())
        )
        .daemon(false)
        .processNameSuffix("file_service")
        .debuggable(true);

        try {
            Shizuku.bindUserService(args, serviceConnection);
        } catch (Throwable e) {
            Log.e(TAG, "Shizuku bindUserService failed", e);
            isBinding = false;
        }
    }

    public static String executeCommand(String[] commands) {
        return "DEPRECATED: Use service methods";
    }

    public static List<FileItem> listFiles(String dirPath) {
        List<FileItem> items = new ArrayList<>();
        if (fileService == null) {
            Log.e(TAG, "Cannot list files: FileService not bound");
            return items;
        }

        try {
            String sanitizedPath = dirPath;
            if (sanitizedPath.endsWith("/")) {
                sanitizedPath = sanitizedPath.substring(0, sanitizedPath.length() - 1);
            }
            List<String> rawList = fileService.listFiles(sanitizedPath);
            for (String line : rawList) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String name = parts[0];
                    long size = 0;
                    try { size = Long.parseLong(parts[1]); } catch (Exception ignored) {}
                    long mtime = 0;
                    try { mtime = Long.parseLong(parts[2]); } catch (Exception ignored) {}
                    String type = parts[3];
                    items.add(new FileItem(
                            name,
                            sanitizedPath + "/" + name,
                            size,
                            mtime,
                            "dir".equals(type)
                    ));
                }
            }

            // Sort: directories first, then alphabetically
            items.sort((o1, o2) -> {
                if (o1.isDirectory() != o2.isDirectory()) {
                    return o1.isDirectory() ? -1 : 1;
                }
                return o1.getName().compareToIgnoreCase(o2.getName());
            });
        } catch (Throwable e) {
            Log.e(TAG, "listFiles via AIDL failed", e);
        }
        return items;
    }

    public static boolean createFolder(String parentPath, String folderName) {
        if (fileService == null) return false;
        try {
            String parent = parentPath;
            if (parent.endsWith("/")) {
                parent = parent.substring(0, parent.length() - 1);
            }
            return fileService.createFolder(parent, folderName);
        } catch (Throwable e) {
            Log.e(TAG, "createFolder via AIDL failed", e);
            return false;
        }
    }

    public static boolean createFile(String parentPath, String fileName, String content) {
        if (fileService == null) return false;
        try {
            String parent = parentPath;
            if (parent.endsWith("/")) {
                parent = parent.substring(0, parent.length() - 1);
            }
            String filePath = parent + "/" + fileName;
            return fileService.writeFile(filePath, content);
        } catch (Throwable e) {
            Log.e(TAG, "createFile via AIDL failed", e);
            return false;
        }
    }

    public static boolean deleteFileOrFolder(String path) {
        if (fileService == null) return false;
        try {
            return fileService.deletePath(path);
        } catch (Throwable e) {
            Log.e(TAG, "deleteFileOrFolder via AIDL failed", e);
            return false;
        }
    }

    public static boolean renameFileOrFolder(String oldPath, String newName) {
        if (fileService == null) return false;
        try {
            int lastSlash = oldPath.lastIndexOf('/');
            String parentDir = lastSlash >= 0 ? oldPath.substring(0, lastSlash) : "";
            String newPath = parentDir.isEmpty() ? newName : parentDir + "/" + newName;
            return fileService.renamePath(oldPath, newPath);
        } catch (Throwable e) {
            Log.e(TAG, "renameFileOrFolder via AIDL failed", e);
            return false;
        }
    }

    public static String readTextFile(String filePath) {
        if (fileService == null) return "ERROR: FileService not bound";
        try {
            return fileService.readFile(filePath);
        } catch (Throwable e) {
            Log.e(TAG, "readTextFile via AIDL failed", e);
            return "ERROR: " + e.getLocalizedMessage();
        }
    }

    public static boolean writeTextFile(String filePath, String content) {
        if (fileService == null) return false;
        try {
            return fileService.writeFile(filePath, content);
        } catch (Throwable e) {
            Log.e(TAG, "writeTextFile via AIDL failed", e);
            return false;
        }
    }

    public static boolean importFile(Context context, Uri sourceUri, String destPath) {
        if (fileService == null) return false;
        File tempFile = new File(context.getCacheDir(), "import_temp_" + System.currentTimeMillis());
        try {
            // Step 1: Client writes Uri stream to local cache file
            try (InputStream input = context.getContentResolver().openInputStream(sourceUri);
                 OutputStream output = new java.io.FileOutputStream(tempFile)) {
                if (input == null) return false;
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                output.flush();
            }

            // Step 2: UserService copies the cached file to the privileged destPath
            return fileService.copyFile(tempFile.getAbsolutePath(), destPath);
        } catch (Throwable e) {
            Log.e(TAG, "Import file via AIDL failed", e);
            return false;
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public static boolean exportFile(Context context, String sourcePath, Uri destUri) {
        if (fileService == null) return false;
        File tempFile = new File(context.getCacheDir(), "export_temp_" + System.currentTimeMillis());
        try {
            // Step 1: UserService copies the privileged file to local cache
            boolean success = fileService.copyFile(sourcePath, tempFile.getAbsolutePath());
            if (!success) return false;

            // Step 2: Client writes cache file to Uri stream
            try (InputStream input = new java.io.FileInputStream(tempFile);
                 OutputStream output = context.getContentResolver().openOutputStream(destUri)) {
                if (output == null) return false;
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                output.flush();
            }
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "Export file via AIDL failed", e);
            return false;
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}

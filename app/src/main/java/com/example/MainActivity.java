package com.example;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.data.BookmarkEntity;
import com.example.data.FileItem;
import com.example.data.FileManagerRepository;
import com.example.data.RecentFileEntity;
import com.example.data.ShizukuShellManager;
import com.example.databinding.ActivityMainBinding;

import rikka.shizuku.Shizuku;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements FileAdapter.OnItemClickListener {

    private static final int REQUEST_CODE_SHIZUKU = 1001;

    private ActivityMainBinding binding;
    private FileManagerRepository repository;
    private FileAdapter fileAdapter;
    private String currentPath = "/sdcard/Android";
    private boolean isCurrentPathBookmarked = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        runOnUiThread(this::onBinderAttached);
    };

    private final ShizukuShellManager.OnBinderStateChangeListener binderStateListener = (bound) -> {
        if (bound) {
            runOnUiThread(this::checkShizukuStatusAndLoad);
        } else {
            runOnUiThread(() -> updatePermissionState(false, "Service Disconnected"));
        }
    };

    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener = 
            (requestCode, grantResult) -> {
                if (requestCode == REQUEST_CODE_SHIZUKU) {
                    boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
                    if (granted) {
                        pollAndVerifyPermission(5, 150);
                    } else {
                        updatePermissionState(false, "Permission Denied");
                    }
                }
            };

    private final ActivityResultLauncher<String> importLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    String name = getFileNameFromUri(this, uri);
                    if (name == null) name = "imported_file";
                    importFile(uri, name);
                }
            }
    );

    private FileItem pendingExportItem = null;
    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("*/*"),
            uri -> {
                if (uri != null && pendingExportItem != null) {
                    exportFile(pendingExportItem, uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new FileManagerRepository(this);

        setupRecyclerView();
        setupClickListeners();

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        ShizukuShellManager.addListener(binderStateListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        initShizukuLifecycle();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        } catch (Throwable ignored) {}
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
        } catch (Throwable ignored) {}
        ShizukuShellManager.removeListener(binderStateListener);
    }

    private void setupRecyclerView() {
        fileAdapter = new FileAdapter(this);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(fileAdapter);
    }

    private void setupClickListeners() {
        binding.btnAuthorizePermission.setOnClickListener(v -> {
            try {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU);
            } catch (Throwable e) {
                Toast.makeText(this, "Failed to request permission: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnNavigateUp.setOnClickListener(v -> navigateUp());

        binding.btnRefresh.setOnClickListener(v -> refreshFiles());

        binding.btnBookmarkToggle.setOnClickListener(v -> toggleCurrentPathBookmark());

        binding.btnShowBookmarks.setOnClickListener(v -> showBookmarksDialog());

        binding.btnRecentActions.setOnClickListener(v -> showRecentActionsDialog());

        binding.fabAdd.setOnClickListener(v -> showAddOptionsPopupMenu(v));
    }

    private void initShizukuLifecycle() {
        if (ShizukuShellManager.isShizukuRunning()) {
            onBinderAttached();
        } else {
            updatePermissionState(false, "Waiting for Shizuku Binder...");
        }
    }

    private void onBinderAttached() {
        ShizukuShellManager.bindService(this, () -> {
            runOnUiThread(this::checkShizukuStatusAndLoad);
        });
    }

    private void pollAndVerifyPermission(int maxRetries, long delayMs) {
        final int[] attempts = {0};
        Runnable checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (ShizukuShellManager.hasShizukuPermission()) {
                    onBinderAttached();
                } else if (attempts[0] < maxRetries) {
                    attempts[0]++;
                    mainHandler.postDelayed(this, delayMs);
                } else {
                    updatePermissionState(false, "Awaiting Permission");
                }
            }
        };
        mainHandler.post(checkRunnable);
    }

    private void checkShizukuStatusAndLoad() {
        if (ShizukuShellManager.isShizukuRunning()) {
            boolean granted = ShizukuShellManager.hasShizukuPermission();
            updatePermissionState(granted, granted ? "Permission Active" : "Awaiting Permission");
        } else {
            updatePermissionState(false, "Waiting for Shizuku Binder...");
        }
    }

    private void updatePermissionState(boolean granted, String message) {
        if (granted && ShizukuShellManager.isServiceBound()) {
            int uid = ShizukuShellManager.getShizukuUid();
            String mode = (uid == 0) ? "Root" : (uid == 2000) ? "ADB" : "UID " + uid;
            boolean canGrant = ShizukuShellManager.checkShizukuPermission("android.permission.GRANT_RUNTIME_PERMISSIONS");
            String statusText = "Active (" + mode + (canGrant ? ", PM Access" : "") + ")";

            binding.toolbarSubtitle.setText(statusText);
            binding.toolbarSubtitle.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            binding.setupOnboardingContainer.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(View.VISIBLE);
            binding.fabAdd.setVisibility(View.VISIBLE);
            refreshFiles();
        } else {
            binding.toolbarSubtitle.setText(message != null ? message : "Awaiting Permission");
            binding.toolbarSubtitle.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            binding.setupOnboardingContainer.setVisibility(View.VISIBLE);
            binding.recyclerView.setVisibility(View.GONE);
            binding.fabAdd.setVisibility(View.GONE);
        }
    }

    private void refreshFiles() {
        binding.loadingSpinner.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                List<FileItem> items = ShizukuShellManager.listFiles(currentPath);
                boolean isBookmarked = repository.isBookmarked(currentPath);
                mainHandler.post(() -> {
                    binding.loadingSpinner.setVisibility(View.GONE);
                    fileAdapter.setItems(items);
                    updateBookmarkState(isBookmarked);
                    binding.tvCurrentPath.setText(currentPath);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    binding.loadingSpinner.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Failed to load: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateBookmarkState(boolean isBookmarked) {
        isCurrentPathBookmarked = isBookmarked;
        if (isBookmarked) {
            binding.btnBookmarkToggle.setImageResource(android.R.drawable.btn_star_big_on);
        } else {
            binding.btnBookmarkToggle.setImageResource(android.R.drawable.btn_star_big_off);
        }
    }

    private void toggleCurrentPathBookmark() {
        executor.execute(() -> {
            boolean isBookmarked = repository.isBookmarked(currentPath);
            if (isBookmarked) {
                repository.removeBookmark(currentPath);
                mainHandler.post(() -> {
                    updateBookmarkState(false);
                    Toast.makeText(MainActivity.this, "Bookmark removed", Toast.LENGTH_SHORT).show();
                });
            } else {
                String name = currentPath.substring(currentPath.lastIndexOf('/') + 1);
                if (name.isEmpty()) name = "Android";
                repository.addBookmark(currentPath, name);
                mainHandler.post(() -> {
                    updateBookmarkState(true);
                    Toast.makeText(MainActivity.this, "Bookmark added", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void navigateTo(String path) {
        currentPath = path;
        refreshFiles();
    }

    private void navigateUp() {
        if (currentPath.equals("/sdcard") || currentPath.equals("/") || currentPath.equals("/sdcard/Android")) {
            if (currentPath.equals("/sdcard/Android")) {
                navigateTo("/sdcard");
            }
            return;
        }
        int idx = currentPath.lastIndexOf('/');
        if (idx > 0) {
            navigateTo(currentPath.substring(0, idx));
        }
    }

    @Override
    public void onItemClick(FileItem item) {
        if (item.isDirectory()) {
            navigateTo(item.getPath());
        } else {
            openTextFileForEditing(item);
        }
    }

    @Override
    public void onItemMenuClick(FileItem item, View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenu().add("Rename");
        popup.getMenu().add("Delete");
        if (!item.isDirectory()) {
            popup.getMenu().add("Export");
        }

        popup.setOnMenuItemClickListener(menuItem -> {
            String title = menuItem.getTitle().toString();
            if ("Rename".equals(title)) {
                showRenameDialog(item);
                return true;
            } else if ("Delete".equals(title)) {
                showDeleteConfirmDialog(item);
                return true;
            } else if ("Export".equals(title)) {
                pendingExportItem = item;
                exportLauncher.launch(item.getName());
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showAddOptionsPopupMenu(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenu().add("Create Folder");
        popup.getMenu().add("Create File");
        popup.getMenu().add("Import File");

        popup.setOnMenuItemClickListener(menuItem -> {
            String title = menuItem.getTitle().toString();
            if ("Create Folder".equals(title)) {
                showCreateFolderDialog();
                return true;
            } else if ("Create File".equals(title)) {
                showCreateFileDialog();
                return true;
            } else if ("Import File".equals(title)) {
                importLauncher.launch("*/*");
                return true;
            }
            return false;
        });
        popup.show();
    }

    // --- Create Folder ---
    private void showCreateFolderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create Folder");
        final EditText input = new EditText(this);
        input.setHint("Folder name");
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (!folderName.isEmpty()) {
                createFolder(folderName);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void createFolder(String folderName) {
        binding.loadingSpinner.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            boolean success = ShizukuShellManager.createFolder(currentPath, folderName);
            mainHandler.post(() -> {
                binding.loadingSpinner.setVisibility(View.GONE);
                if (success) {
                    Toast.makeText(MainActivity.this, "Folder created", Toast.LENGTH_SHORT).show();
                    repository.addRecentFile(currentPath + "/" + folderName, folderName, "CREATE");
                    refreshFiles();
                } else {
                    Toast.makeText(MainActivity.this, "Failed to create folder", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // --- Create File ---
    private void showCreateFileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create File");
        final EditText input = new EditText(this);
        input.setHint("File name (e.g., info.txt)");
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String fileName = input.getText().toString().trim();
            if (!fileName.isEmpty()) {
                createFile(fileName);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void createFile(String fileName) {
        binding.loadingSpinner.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            boolean success = ShizukuShellManager.createFile(currentPath, fileName, "");
            mainHandler.post(() -> {
                binding.loadingSpinner.setVisibility(View.GONE);
                if (success) {
                    Toast.makeText(MainActivity.this, "File created", Toast.LENGTH_SHORT).show();
                    repository.addRecentFile(currentPath + "/" + fileName, fileName, "CREATE");
                    refreshFiles();
                } else {
                    Toast.makeText(MainActivity.this, "Failed to create file", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // --- Rename ---
    private void showRenameDialog(FileItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename " + item.getName());
        final EditText input = new EditText(this);
        input.setText(item.getName());
        builder.setView(input);

        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(item.getName())) {
                renameItem(item, newName);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void renameItem(FileItem item, String newName) {
        binding.loadingSpinner.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            boolean success = ShizukuShellManager.renameFileOrFolder(item.getPath(), newName);
            mainHandler.post(() -> {
                binding.loadingSpinner.setVisibility(View.GONE);
                if (success) {
                    Toast.makeText(MainActivity.this, "Renamed successfully", Toast.LENGTH_SHORT).show();
                    String parentDir = item.getPath().substring(0, item.getPath().lastIndexOf('/'));
                    repository.addRecentFile(parentDir + "/" + newName, newName, "EDIT");
                    refreshFiles();
                } else {
                    Toast.makeText(MainActivity.this, "Failed to rename", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // --- Delete ---
    private void showDeleteConfirmDialog(FileItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + item.getName())
                .setMessage("Are you sure you want to permanently delete this item? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteItem(item))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteItem(FileItem item) {
        binding.loadingSpinner.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            boolean success = ShizukuShellManager.deleteFileOrFolder(item.getPath());
            mainHandler.post(() -> {
                binding.loadingSpinner.setVisibility(View.GONE);
                if (success) {
                    Toast.makeText(MainActivity.this, "Deleted successfully", Toast.LENGTH_SHORT).show();
                    repository.addRecentFile(item.getPath(), item.getName(), "DELETE");
                    refreshFiles();
                } else {
                    Toast.makeText(MainActivity.this, "Failed to delete", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // --- Built-in Text Editor ---
    private void openTextFileForEditing(FileItem item) {
        binding.loadingSpinner.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            String content = ShizukuShellManager.readTextFile(item.getPath());
            mainHandler.post(() -> {
                binding.loadingSpinner.setVisibility(View.GONE);
                if (content.startsWith("ERROR:")) {
                    Toast.makeText(MainActivity.this, "Error reading file: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    repository.addRecentFile(item.getPath(), item.getName(), "VIEW");
                    showTextEditorDialog(item, content);
                }
            });
        });
    }

    private void showTextEditorDialog(FileItem item, String initialContent) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_text_editor);

        TextView title = dialog.findViewById(R.id.editor_title);
        EditText contentInput = dialog.findViewById(R.id.et_editor_content);
        View btnClose = dialog.findViewById(R.id.btn_close_editor);
        View btnSave = dialog.findViewById(R.id.btn_save_editor);

        title.setText(item.getName());
        contentInput.setText(initialContent);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String content = contentInput.getText().toString();
            binding.loadingSpinner.setVisibility(View.VISIBLE);
            executor.execute(() -> {
                boolean success = ShizukuShellManager.writeTextFile(item.getPath(), content);
                mainHandler.post(() -> {
                    binding.loadingSpinner.setVisibility(View.GONE);
                    if (success) {
                        Toast.makeText(MainActivity.this, "Saved successfully", Toast.LENGTH_SHORT).show();
                        repository.addRecentFile(item.getPath(), item.getName(), "EDIT");
                        dialog.dismiss();
                        refreshFiles();
                    } else {
                        Toast.makeText(MainActivity.this, "Failed to save file", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });

        dialog.show();
    }

    // --- Bookmarks Dialog ---
    private void showBookmarksDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_list_view);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView title = dialog.findViewById(R.id.dialog_title);
        TextView emptyText = dialog.findViewById(R.id.tv_empty_state);
        androidx.recyclerview.widget.RecyclerView rv = dialog.findViewById(R.id.dialog_recycler_view);
        View btnClose = dialog.findViewById(R.id.btn_dialog_close);

        title.setText("Saved Bookmarks");
        rv.setLayoutManager(new LinearLayoutManager(this));

        DialogRowAdapter adapter = new DialogRowAdapter(new DialogRowAdapter.OnRowActionListener() {
            @Override
            public void onRowClick(DialogRowAdapter.DialogRowItem item) {
                navigateTo(item.subtitle);
                dialog.dismiss();
            }

            @Override
            public void onRowActionClick(DialogRowAdapter.DialogRowItem item) {
                executor.execute(() -> {
                    repository.removeBookmark(item.subtitle);
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "Bookmark deleted", Toast.LENGTH_SHORT).show();
                        // Adapter updates dynamically due to LiveData observation
                    });
                });
            }
        });
        rv.setAdapter(adapter);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Observe bookmarks
        repository.getBookmarks().observe(this, bookmarks -> {
            if (bookmarks == null || bookmarks.isEmpty()) {
                emptyText.setVisibility(View.VISIBLE);
                rv.setVisibility(View.GONE);
                adapter.setItems(new ArrayList<>());
            } else {
                emptyText.setVisibility(View.GONE);
                rv.setVisibility(View.VISIBLE);
                List<DialogRowAdapter.DialogRowItem> rowItems = new ArrayList<>();
                for (BookmarkEntity b : bookmarks) {
                    rowItems.add(new DialogRowAdapter.DialogRowItem(0, b.name, b.path, true));
                }
                adapter.setItems(rowItems);
            }
        });

        dialog.show();
    }

    // --- Recent Actions Dialog ---
    private void showRecentActionsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_list_view);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView title = dialog.findViewById(R.id.dialog_title);
        TextView emptyText = dialog.findViewById(R.id.tv_empty_state);
        androidx.recyclerview.widget.RecyclerView rv = dialog.findViewById(R.id.dialog_recycler_view);
        com.google.android.material.button.MaterialButton btnClear = dialog.findViewById(R.id.btn_dialog_secondary);
        View btnClose = dialog.findViewById(R.id.btn_dialog_close);

        title.setText("Recent Activities");
        btnClear.setText("Clear Recents");
        btnClear.setVisibility(View.VISIBLE);
        rv.setLayoutManager(new LinearLayoutManager(this));

        DialogRowAdapter adapter = new DialogRowAdapter(new DialogRowAdapter.OnRowActionListener() {
            @Override
            public void onRowClick(DialogRowAdapter.DialogRowItem item) {
                // Navigate to directory or parent directory of the file
                String path = item.subtitle;
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash > 0) {
                    navigateTo(path.substring(0, lastSlash));
                } else {
                    navigateTo(path);
                }
                dialog.dismiss();
            }

            @Override
            public void onRowActionClick(DialogRowAdapter.DialogRowItem item) {
                executor.execute(() -> {
                    repository.deleteRecentFile(item.id);
                });
            }
        });
        rv.setAdapter(adapter);

        btnClear.setOnClickListener(v -> {
            executor.execute(() -> {
                repository.clearRecents();
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Recents cleared", Toast.LENGTH_SHORT).show());
            });
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Observe recents
        repository.getRecentFiles().observe(this, recents -> {
            if (recents == null || recents.isEmpty()) {
                emptyText.setVisibility(View.VISIBLE);
                rv.setVisibility(View.GONE);
                btnClear.setEnabled(false);
                adapter.setItems(new ArrayList<>());
            } else {
                emptyText.setVisibility(View.GONE);
                rv.setVisibility(View.VISIBLE);
                btnClear.setEnabled(true);
                List<DialogRowAdapter.DialogRowItem> rowItems = new ArrayList<>();
                for (RecentFileEntity r : recents) {
                    String actionAndName = r.actionType + ": " + r.name;
                    rowItems.add(new DialogRowAdapter.DialogRowItem(r.id, actionAndName, r.path, false));
                }
                adapter.setItems(rowItems);
            }
        });

        dialog.show();
    }

    // --- Import File ---
    private void importFile(Uri sourceUri, String destFileName) {
        binding.loadingSpinner.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            String destPath = currentPath + "/" + destFileName;
            boolean success = ShizukuShellManager.importFile(this, sourceUri, destPath);
            mainHandler.post(() -> {
                binding.loadingSpinner.setVisibility(View.GONE);
                if (success) {
                    Toast.makeText(MainActivity.this, "Imported " + destFileName + " successfully", Toast.LENGTH_SHORT).show();
                    repository.addRecentFile(destPath, destFileName, "CREATE");
                    refreshFiles();
                } else {
                    Toast.makeText(MainActivity.this, "Failed to import file", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // --- Export File ---
    private void exportFile(FileItem item, Uri destUri) {
        binding.loadingSpinner.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            boolean success = ShizukuShellManager.exportFile(this, item.getPath(), destUri);
            mainHandler.post(() -> {
                binding.loadingSpinner.setVisibility(View.GONE);
                if (success) {
                    Toast.makeText(MainActivity.this, "Exported " + item.getName() + " successfully", Toast.LENGTH_SHORT).show();
                    repository.addRecentFile(item.getPath(), item.getName(), "VIEW");
                } else {
                    Toast.makeText(MainActivity.this, "Failed to export file", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private String getFileNameFromUri(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}

package com.example.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileManagerRepository {
    private final BookmarkDao bookmarkDao;
    private final RecentFileDao recentFileDao;
    private final LiveData<List<BookmarkEntity>> bookmarks;
    private final LiveData<List<RecentFileEntity>> recentFiles;
    private final ExecutorService executorService;

    public FileManagerRepository(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        bookmarkDao = db.bookmarkDao();
        recentFileDao = db.recentFileDao();
        bookmarks = bookmarkDao.getAllBookmarks();
        recentFiles = recentFileDao.getRecentFiles();
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<BookmarkEntity>> getBookmarks() {
        return bookmarks;
    }

    public LiveData<List<RecentFileEntity>> getRecentFiles() {
        return recentFiles;
    }

    public void addBookmark(String path, String name) {
        executorService.execute(() -> {
            bookmarkDao.insertBookmark(new BookmarkEntity(path, name, System.currentTimeMillis()));
        });
    }

    public void removeBookmark(String path) {
        executorService.execute(() -> {
            bookmarkDao.deleteBookmark(path);
        });
    }

    public boolean isBookmarked(String path) {
        return bookmarkDao.isBookmarked(path);
    }

    public void addRecentFile(String path, String name, String actionType) {
        executorService.execute(() -> {
            recentFileDao.insertRecentFile(new RecentFileEntity(0, path, name, actionType, System.currentTimeMillis()));
        });
    }

    public void deleteRecentFile(int id) {
        executorService.execute(() -> {
            recentFileDao.deleteRecentFile(id);
        });
    }

    public void clearRecents() {
        executorService.execute(() -> {
            recentFileDao.clearAll();
        });
    }
}

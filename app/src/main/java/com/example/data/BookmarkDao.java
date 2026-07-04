package com.example.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY addedAt DESC")
    LiveData<List<BookmarkEntity>> getAllBookmarks();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBookmark(BookmarkEntity bookmark);

    @Query("DELETE FROM bookmarks WHERE path = :path")
    void deleteBookmark(String path);

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE path = :path)")
    boolean isBookmarked(String path);
}

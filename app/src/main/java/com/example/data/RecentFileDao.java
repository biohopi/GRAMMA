package com.example.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY timestamp DESC LIMIT 20")
    LiveData<List<RecentFileEntity>> getRecentFiles();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecentFile(RecentFileEntity recent);

    @Query("DELETE FROM recent_files WHERE id = :id")
    void deleteRecentFile(int id);

    @Query("DELETE FROM recent_files")
    void clearAll();
}

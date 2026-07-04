package com.example.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "bookmarks")
public class BookmarkEntity {
    @PrimaryKey
    @NonNull
    public String path;
    public String name;
    public long addedAt;

    public BookmarkEntity(@NonNull String path, String name, long addedAt) {
        this.path = path;
        this.name = name;
        this.addedAt = addedAt;
    }
}

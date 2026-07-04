package com.example.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "recent_files")
public class RecentFileEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String path;
    public String name;
    public String actionType; // "VIEW", "EDIT", "CREATE"
    public long timestamp;

    public RecentFileEntity(int id, String path, String name, String actionType, long timestamp) {
        this.id = id;
        this.path = path;
        this.name = name;
        this.actionType = actionType;
        this.timestamp = timestamp;
    }
}

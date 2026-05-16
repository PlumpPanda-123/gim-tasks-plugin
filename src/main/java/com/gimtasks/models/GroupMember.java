package com.gimtasks.models;

import lombok.Data;

@Data
public class GroupMember {
    private final String username;
    private int tasksAssigned;

    public GroupMember(String username) {
        this.username      = username;
        this.tasksAssigned = 0;
    }

    public void incrementTasks() {
        tasksAssigned++;
    }
}

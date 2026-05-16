package com.gimtasks.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Task {
    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("skill")
    private String skill;

    @SerializedName("quantity")
    private int quantity;

    @SerializedName("quantityCompleted")
    private int quantityCompleted;

    @SerializedName("assignee")
    private String assignee;

    @SerializedName("createdBy")
    private String createdBy;

    @SerializedName("status")
    private String status;

    @SerializedName("priority")
    private String priority;

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    public boolean isUrgent() {
        return "URGENT".equals(priority);
    }

    public int progressPercent() {
        if (quantity <= 0) return 0;
        return Math.min(100, (int) ((quantityCompleted / (double) quantity) * 100));
    }
}

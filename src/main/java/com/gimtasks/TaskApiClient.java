package com.gimtasks;

import com.gimtasks.models.Task;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class TaskApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final Gson gson;
    private final GimTasksConfig config;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TaskApiClient(OkHttpClient http, Gson gson, GimTasksConfig config) {
        this.http   = http;
        this.gson   = gson;
        this.config = config;
    }

    // ── Public async API ──────────────────────────────────────────────────────

    public void fetchTasks(Consumer<List<Task>> onSuccess, Runnable onError) {
        executor.submit(() -> {
            try {
                Request req = baseRequest("/tasks").get().build();
                List<Task> tasks = executeListRequest(req);
                onSuccess.accept(tasks);
            } catch (Exception e) {
                log.warn("GIM Tasks: failed to fetch tasks", e);
                onError.run();
            }
        });
    }

    public void createTask(String name, String skill, int quantity,
                           String assignee, String priority,
                           Consumer<Task> onSuccess, Runnable onError) {
        executor.submit(() -> {
            try {
                String body = gson.toJson(new CreateTaskRequest(
                    name, skill, quantity, assignee,
                    config.playerUsername(), priority));
                Request req = baseRequest("/tasks")
                    .post(RequestBody.create(JSON, body))
                    .build();
                Task task = executeSingleRequest(req);
                onSuccess.accept(task);
            } catch (Exception e) {
                log.warn("GIM Tasks: failed to create task", e);
                onError.run();
            }
        });
    }

    public void assignTask(String taskId, String assignee,
                           Consumer<Task> onSuccess, Runnable onError) {
        executor.submit(() -> {
            try {
                String body = gson.toJson(Collections.singletonMap("assignee", assignee));
                Request req = baseRequest("/tasks/" + taskId + "/assign")
                    .patch(RequestBody.create(JSON, body))
                    .build();
                onSuccess.accept(executeSingleRequest(req));
            } catch (Exception e) {
                log.warn("GIM Tasks: failed to assign task", e);
                onError.run();
            }
        });
    }

    public void updateStatus(String taskId, String status,
                             Consumer<Task> onSuccess, Runnable onError) {
        executor.submit(() -> {
            try {
                String body = gson.toJson(Collections.singletonMap("status", status));
                Request req = baseRequest("/tasks/" + taskId + "/status")
                    .patch(RequestBody.create(JSON, body))
                    .build();
                onSuccess.accept(executeSingleRequest(req));
            } catch (Exception e) {
                log.warn("GIM Tasks: failed to update status", e);
                onError.run();
            }
        });
    }

    public void updateProgress(String taskId, int quantityCompleted,
                               Consumer<Task> onSuccess, Runnable onError) {
        executor.submit(() -> {
            try {
                String body = gson.toJson(Collections.singletonMap("quantityCompleted", quantityCompleted));
                Request req = baseRequest("/tasks/" + taskId + "/progress")
                    .patch(RequestBody.create(JSON, body))
                    .build();
                onSuccess.accept(executeSingleRequest(req));
            } catch (Exception e) {
                log.warn("GIM Tasks: failed to update progress", e);
                onError.run();
            }
        });
    }

    public void deleteTask(String taskId, Runnable onSuccess, Runnable onError) {
        executor.submit(() -> {
            try {
                Request req = baseRequest("/tasks/" + taskId).delete().build();
                try (Response resp = http.newCall(req).execute()) {
                    if (resp.isSuccessful()) onSuccess.run();
                    else onError.run();
                }
            } catch (Exception e) {
                log.warn("GIM Tasks: failed to delete task", e);
                onError.run();
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Request.Builder baseRequest(String path) {
        String url = config.backendUrl().replaceAll("/$", "") + path;
        return new Request.Builder()
            .url(url)
            .addHeader("x-api-key",   config.apiKey())
            .addHeader("x-group-id",  config.groupId())
            .addHeader("Content-Type","application/json");
    }

    private List<Task> executeListRequest(Request req) throws IOException {
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            Type type = new TypeToken<List<Task>>(){}.getType();
            return gson.fromJson(resp.body().string(), type);
        }
    }

    private Task executeSingleRequest(Request req) throws IOException {
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            return gson.fromJson(resp.body().string(), Task.class);
        }
    }

    // ── Inner request DTOs ────────────────────────────────────────────────────

    private static class CreateTaskRequest {
        String name, skill, assignee, createdBy, priority;
        int quantity;

        CreateTaskRequest(String name, String skill, int quantity,
                          String assignee, String createdBy, String priority) {
            this.name      = name;
            this.skill     = skill;
            this.quantity  = quantity;
            this.assignee  = (assignee == null || assignee.isEmpty()) ? null : assignee;
            this.createdBy = createdBy;
            this.priority  = priority;
        }
    }
}

package com.gimtasks;

import com.gimtasks.models.Task;
import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
    name        = "GIM Tasks",
    description = "Shared task board for Group Ironman teams",
    tags        = { "group", "ironman", "tasks", "gim" }
)
public class GimTasksPlugin extends Plugin {

    @Inject private Client client;
    @Inject private ClientToolbar clientToolbar;
    @Inject private GimTasksConfig config;
    @Inject private GimTasksOverlay overlay;
    @Inject private OverlayManager overlayManager;
    @Inject private SkillIconManager skillIconManager;
    @Inject private OkHttpClient okHttpClient;
    @Inject private Gson gson;

    private TaskApiClient apiClient;
    private GimTasksPanel panel;
    private NavigationButton navButton;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pollFuture;

    // Track which tasks have been notified to avoid repeat toasts
    private final List<String> notifiedTaskIds = new ArrayList<>();
    private List<Task> lastKnownTasks         = new ArrayList<>();
    private List<String> groupMembers         = new ArrayList<>();
    private boolean apiError                  = false;

    @Override
    protected void startUp() {
        apiClient = new TaskApiClient(okHttpClient, gson, config);

        panel = new GimTasksPanel(apiClient, config, skillIconManager, this::pollNow);

        BufferedImage icon;
        try {
            icon = ImageUtil.loadImageResource(getClass(), "icon.png");
        } catch (IllegalArgumentException e) {
            icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }

        navButton = NavigationButton.builder()
            .tooltip("GIM Tasks")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);
        overlayManager.add(overlay);

        schedulePoll();
        log.info("GIM Tasks plugin started");
    }

    @Override
    protected void shutDown() {
        if (pollFuture != null) pollFuture.cancel(true);
        scheduler.shutdownNow();
        apiClient.shutdown();
        clientToolbar.removeNavigation(navButton);
        overlayManager.remove(overlay);
        log.info("GIM Tasks plugin stopped");
    }

    @Provides
    GimTasksConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GimTasksConfig.class);
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private void schedulePoll() {
        int interval = Math.max(5, config.pollIntervalSeconds());
        if (pollFuture != null) pollFuture.cancel(false);
        pollFuture = scheduler.scheduleAtFixedRate(this::pollNow, 0, interval, TimeUnit.SECONDS);
    }

    private void pollNow() {
        if (config.backendUrl().isEmpty() || config.apiKey().isEmpty() || config.groupId().isEmpty()) {
            log.debug("GIM Tasks: config incomplete, skipping poll");
            return;
        }

        // Fetch group members first (or use cached list)
        fetchGroupMembers();
        fetchTasks();
    }

    private void fetchGroupMembers() {
        // Reuse the cached list between polls; group members rarely change
        if (!groupMembers.isEmpty()) return;

        // Simple GET /group via the API client's executor (handled inline via OkHttp)
        // We perform this synchronously on the same background thread as the poll
        try {
            okhttp3.Request req = new okhttp3.Request.Builder()
                .url(config.backendUrl().replaceAll("/$", "") + "/group")
                .addHeader("x-api-key",  config.apiKey())
                .addHeader("x-group-id", config.groupId())
                .build();

            try (okhttp3.Response resp = okHttpClient.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    GroupResponse gr = gson.fromJson(resp.body().string(), GroupResponse.class);
                    if (gr != null && gr.members != null) {
                        groupMembers = gr.members;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("GIM Tasks: failed to fetch group members", e);
        }
    }

    private void fetchTasks() {
        apiClient.fetchTasks(
            tasks -> {
                apiError = false;
                checkForNewAssignments(tasks);
                lastKnownTasks = tasks;

                String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));

                SwingUtilities.invokeLater(() -> {
                    panel.updateTasks(tasks, groupMembers);
                    panel.setSyncTime(timestamp);
                });
            },
            () -> {
                apiError = true;
                SwingUtilities.invokeLater(() -> panel.setSyncTime("Error — check config"));
            }
        );
    }

    private void checkForNewAssignments(List<Task> freshTasks) {
        String myUsername = config.playerUsername();
        if (myUsername == null || myUsername.isEmpty()) return;

        for (Task fresh : freshTasks) {
            if (!myUsername.equalsIgnoreCase(fresh.getAssignee())) continue;
            if (notifiedTaskIds.contains(fresh.getId())) continue;

            // Check whether this task was NOT assigned to us before
            boolean wasAlreadyMine = lastKnownTasks.stream()
                .filter(t -> t.getId().equals(fresh.getId()))
                .anyMatch(t -> myUsername.equalsIgnoreCase(t.getAssignee()));

            if (!wasAlreadyMine) {
                notifiedTaskIds.add(fresh.getId());
                overlay.showNotification("New task assigned:\n" + fresh.getName()
                    + "\n" + fresh.getSkill() + " x" + fresh.getQuantity());
            }
        }
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    private static class GroupResponse {
        String id;
        String name;
        List<String> members;
    }
}

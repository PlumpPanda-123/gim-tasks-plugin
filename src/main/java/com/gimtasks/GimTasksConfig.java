package com.gimtasks;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("gimtasks")
public interface GimTasksConfig extends Config {

    @ConfigItem(
        keyName    = "backendUrl",
        name       = "Backend URL",
        description = "Full URL of the GIM Tasks backend (e.g. https://my-app.railway.app)",
        position   = 0
    )
    default String backendUrl() {
        return "http://localhost:3000";
    }

    @ConfigItem(
        keyName    = "apiKey",
        name       = "API Key",
        description = "Shared API key (UUID) for your group",
        position   = 1,
        secret     = true
    )
    default String apiKey() {
        return "";
    }

    @ConfigItem(
        keyName    = "groupId",
        name       = "Group ID",
        description = "Your group's UUID from the Firestore init script",
        position   = 2
    )
    default String groupId() {
        return "";
    }

    @ConfigItem(
        keyName    = "playerUsername",
        name       = "Your Username",
        description = "Your RuneScape username (must match the group allowlist exactly)",
        position   = 3
    )
    default String playerUsername() {
        return "";
    }

    @ConfigItem(
        keyName    = "pollIntervalSeconds",
        name       = "Poll Interval (seconds)",
        description = "How often to fetch the task list from the backend",
        position   = 4
    )
    default int pollIntervalSeconds() {
        return 10;
    }
}

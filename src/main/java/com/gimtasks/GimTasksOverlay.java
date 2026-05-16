package com.gimtasks;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

public class GimTasksOverlay extends Overlay {

    private static final int DISPLAY_MILLIS = 5000;
    private static final Color BG_COLOR     = new Color(0, 0, 0, 180);
    private static final Color TITLE_COLOR  = new Color(255, 200, 0);
    private static final Color TEXT_COLOR   = Color.WHITE;
    private static final int PADDING        = 10;

    private final Client client;

    private String notificationMessage = null;
    private Instant shownAt            = null;

    @Inject
    public GimTasksOverlay(Client client) {
        this.client = client;
        setPosition(OverlayPosition.TOP_RIGHT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    public void showNotification(String message) {
        this.notificationMessage = message;
        this.shownAt             = Instant.now();
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (notificationMessage == null || shownAt == null) return null;

        long elapsed = Instant.now().toEpochMilli() - shownAt.toEpochMilli();
        if (elapsed > DISPLAY_MILLIS) {
            notificationMessage = null;
            shownAt             = null;
            return null;
        }

        // Fade out in the last second
        float alpha = elapsed > (DISPLAY_MILLIS - 1000)
            ? 1f - ((elapsed - (DISPLAY_MILLIS - 1000)) / 1000f)
            : 1f;

        FontMetrics fm    = g.getFontMetrics();
        String title      = "GIM Tasks";
        String[] lines    = notificationMessage.split("\n");
        int maxWidth      = fm.stringWidth(title);
        for (String line : lines) maxWidth = Math.max(maxWidth, fm.stringWidth(line));

        int lineHeight = fm.getHeight();
        int boxWidth   = maxWidth + PADDING * 2;
        int boxHeight  = (lines.length + 1) * lineHeight + PADDING * 2;

        // Background
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
        g.setColor(BG_COLOR);
        g.fillRoundRect(0, 0, boxWidth, boxHeight, 8, 8);

        // Title
        g.setColor(TITLE_COLOR);
        g.drawString(title, PADDING, PADDING + lineHeight);

        // Body lines
        g.setColor(TEXT_COLOR);
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], PADDING, PADDING + (i + 2) * lineHeight);
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        return new Dimension(boxWidth, boxHeight);
    }
}

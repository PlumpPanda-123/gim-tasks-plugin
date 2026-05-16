package com.gimtasks;

import com.gimtasks.models.GroupMember;
import com.gimtasks.models.Task;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class GimTasksPanel extends PluginPanel {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color COLOR_UNASSIGNED  = new Color(120, 120, 120);
    private static final Color COLOR_CLAIMED     = new Color(70, 130, 200);
    private static final Color COLOR_IN_PROGRESS = new Color(220, 160, 0);
    private static final Color COLOR_COMPLETED   = new Color(50, 160, 80);
    private static final Color COLOR_URGENT      = new Color(200, 60, 60);
    private static final Color COLOR_PANEL_BG    = new Color(40, 40, 40);
    private static final Color COLOR_CARD_BG     = new Color(55, 55, 55);
    private static final Color COLOR_TEXT        = new Color(220, 220, 220);
    private static final Color COLOR_SUBTEXT     = new Color(150, 150, 150);

    private static final String[] SKILLS = {
        "ATTACK","STRENGTH","DEFENCE","RANGED","PRAYER","MAGIC","RUNECRAFTING",
        "HITPOINTS","CRAFTING","MINING","SMITHING","FISHING","COOKING",
        "FIREMAKING","WOODCUTTING","AGILITY","HERBLORE","THIEVING","FLETCHING",
        "SLAYER","FARMING","CONSTRUCTION","HUNTER","OTHER"
    };

    private final TaskApiClient apiClient;
    private final GimTasksConfig config;
    private final Runnable refreshCallback;

    // UI components
    private JPanel taskListPanel;
    private JPanel addTaskForm;
    private JLabel syncLabel;
    private JPanel memberBar;
    private boolean showingForm = false;

    // Form fields
    private JTextField nameField;
    private JComboBox<String> skillCombo;
    private JSpinner quantitySpinner;
    private JComboBox<String> assigneeCombo;
    private JToggleButton urgentToggle;

    private List<String> memberNames = new ArrayList<>();

    public GimTasksPanel(TaskApiClient apiClient, GimTasksConfig config, Runnable refreshCallback) {
        this.apiClient       = apiClient;
        this.config          = config;
        this.refreshCallback = refreshCallback;

        setBackground(COLOR_PANEL_BG);
        setLayout(new BorderLayout(0, 4));
        setBorder(new EmptyBorder(6, 6, 6, 6));

        add(buildHeader(), BorderLayout.NORTH);

        taskListPanel = new JPanel();
        taskListPanel.setLayout(new BoxLayout(taskListPanel, BoxLayout.Y_AXIS));
        taskListPanel.setBackground(COLOR_PANEL_BG);

        JScrollPane scroll = new JScrollPane(taskListPanel);
        scroll.setBorder(null);
        scroll.setBackground(COLOR_PANEL_BG);
        scroll.getViewport().setBackground(COLOR_PANEL_BG);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);

        memberBar = buildMemberBar(new ArrayList<>());
        add(buildFooter(), BorderLayout.SOUTH);
    }

    // ── Public update method ──────────────────────────────────────────────────

    public void updateTasks(List<Task> tasks, List<String> members) {
        this.memberNames = members;

        SwingUtilities.invokeLater(() -> {
            taskListPanel.removeAll();

            // Build member task count
            List<GroupMember> groupMembers = new ArrayList<>();
            for (String m : members) {
                GroupMember gm = new GroupMember(m);
                for (Task t : tasks) {
                    if (m.equalsIgnoreCase(t.getAssignee())) gm.incrementTasks();
                }
                groupMembers.add(gm);
            }

            // Update assignee combo with current members
            if (assigneeCombo != null) {
                String selected = (String) assigneeCombo.getSelectedItem();
                assigneeCombo.removeAllItems();
                assigneeCombo.addItem("Unassigned");
                for (String m : members) assigneeCombo.addItem(m);
                if (selected != null) assigneeCombo.setSelectedItem(selected);
            }

            // Render task cards
            for (Task task : tasks) {
                taskListPanel.add(buildTaskCard(task, members));
                taskListPanel.add(Box.createRigidArea(new Dimension(0, 4)));
            }

            if (tasks.isEmpty()) {
                JLabel empty = new JLabel("No tasks yet. Click + to add one.");
                empty.setForeground(COLOR_SUBTEXT);
                empty.setAlignmentX(Component.CENTER_ALIGNMENT);
                empty.setBorder(new EmptyBorder(20, 0, 0, 0));
                taskListPanel.add(empty);
            }

            taskListPanel.revalidate();
            taskListPanel.repaint();

            // Update member bar
            Container footer = (Container) getComponent(getComponentCount() - 1);
            footer.removeAll();
            footer.add(buildMemberBarContent(groupMembers), BorderLayout.CENTER);
            footer.add(buildSyncRow(), BorderLayout.SOUTH);
            footer.revalidate();
            footer.repaint();
        });
    }

    public void setSyncTime(String timestamp) {
        SwingUtilities.invokeLater(() -> {
            if (syncLabel != null) syncLabel.setText("Last sync: " + timestamp);
        });
    }

    // ── UI builders ───────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(COLOR_PANEL_BG);
        header.setBorder(new EmptyBorder(0, 0, 4, 0));

        JLabel title = new JLabel("GIM Tasks");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        header.add(title, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.setBackground(COLOR_PANEL_BG);

        JButton addBtn = styledButton("+");
        addBtn.setToolTipText("Add task");
        addBtn.addActionListener(e -> toggleAddForm());

        JButton refreshBtn = styledButton("↻");
        refreshBtn.setToolTipText("Refresh now");
        refreshBtn.addActionListener(e -> refreshCallback.run());

        buttons.add(refreshBtn);
        buttons.add(addBtn);
        header.add(buttons, BorderLayout.EAST);

        // Add task form (hidden by default)
        addTaskForm = buildAddTaskForm();
        addTaskForm.setVisible(false);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(COLOR_PANEL_BG);
        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(addTaskForm, BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel buildAddTaskForm() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(COLOR_CARD_BG);
        form.setBorder(new EmptyBorder(8, 8, 8, 8));

        nameField = new JTextField();
        nameField.setBackground(new Color(70, 70, 70));
        nameField.setForeground(COLOR_TEXT);
        nameField.setCaretColor(Color.WHITE);
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        skillCombo = new JComboBox<>(SKILLS);
        skillCombo.setBackground(new Color(70, 70, 70));
        skillCombo.setForeground(COLOR_TEXT);
        skillCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100000, 1));
        quantitySpinner.setBackground(new Color(70, 70, 70));
        quantitySpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        assigneeCombo = new JComboBox<>();
        assigneeCombo.addItem("Unassigned");
        for (String m : memberNames) assigneeCombo.addItem(m);
        assigneeCombo.setBackground(new Color(70, 70, 70));
        assigneeCombo.setForeground(COLOR_TEXT);
        assigneeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        urgentToggle = new JToggleButton("Normal");
        urgentToggle.setBackground(new Color(70, 70, 70));
        urgentToggle.setForeground(COLOR_TEXT);
        urgentToggle.addActionListener(e ->
            urgentToggle.setText(urgentToggle.isSelected() ? "URGENT" : "Normal"));
        urgentToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JButton submitBtn = new JButton("Add Task");
        submitBtn.setBackground(COLOR_COMPLETED);
        submitBtn.setForeground(Color.WHITE);
        submitBtn.addActionListener(this::submitAddTask);
        submitBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        form.add(formRow("Task Name",  nameField));
        form.add(formRow("Skill",      skillCombo));
        form.add(formRow("Quantity",   quantitySpinner));
        form.add(formRow("Assign To",  assigneeCombo));
        form.add(formRow("Priority",   urgentToggle));
        form.add(Box.createRigidArea(new Dimension(0, 6)));
        form.add(submitBtn);

        return form;
    }

    private JPanel formRow(String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(COLOR_CARD_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel lbl = new JLabel(label + ":");
        lbl.setForeground(COLOR_SUBTEXT);
        lbl.setPreferredSize(new Dimension(75, 24));
        row.add(lbl, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private JPanel buildTaskCard(Task task, List<String> members) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(COLOR_CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(task.isUrgent() ? COLOR_URGENT : COLOR_CARD_BG, 1),
            new EmptyBorder(6, 8, 6, 8)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // Top row: name + priority badge
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setBackground(COLOR_CARD_BG);
        JLabel nameLabel = new JLabel(task.getName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        topRow.add(nameLabel, BorderLayout.CENTER);
        if (task.isUrgent()) {
            JLabel urgentBadge = badge("URGENT", COLOR_URGENT);
            topRow.add(urgentBadge, BorderLayout.EAST);
        }
        card.add(topRow, BorderLayout.NORTH);

        // Middle: skill, progress, assignee
        JPanel midPanel = new JPanel();
        midPanel.setLayout(new BoxLayout(midPanel, BoxLayout.Y_AXIS));
        midPanel.setBackground(COLOR_CARD_BG);

        String progressText = task.getQuantityCompleted() + " / " + task.getQuantity()
            + " (" + task.progressPercent() + "%)";
        midPanel.add(subLabel(task.getSkill() + "  •  " + progressText));

        String assigneeText = task.getAssignee() != null ? task.getAssignee() : "Unassigned";
        midPanel.add(subLabel("Assignee: " + assigneeText));

        // Progress bar
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(task.progressPercent());
        bar.setForeground(COLOR_COMPLETED);
        bar.setBackground(new Color(70, 70, 70));
        bar.setBorderPainted(false);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5));
        midPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        midPanel.add(bar);

        card.add(midPanel, BorderLayout.CENTER);

        // Bottom row: status badge + action buttons
        JPanel bottomRow = new JPanel(new BorderLayout(4, 0));
        bottomRow.setBackground(COLOR_CARD_BG);
        bottomRow.add(badge(task.getStatus(), statusColor(task.getStatus())), BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        actions.setBackground(COLOR_CARD_BG);

        if (!"COMPLETED".equals(task.getStatus())) {
            JButton claimBtn = tinyButton("Claim");
            claimBtn.addActionListener(e ->
                apiClient.assignTask(task.getId(), config.playerUsername(),
                    t -> refreshCallback.run(), refreshCallback::run));
            actions.add(claimBtn);

            if ("CLAIMED".equals(task.getStatus()) || "IN_PROGRESS".equals(task.getStatus())) {
                JButton startBtn = tinyButton("Start");
                startBtn.addActionListener(e ->
                    apiClient.updateStatus(task.getId(), "IN_PROGRESS",
                        t -> refreshCallback.run(), refreshCallback::run));
                actions.add(startBtn);

                JButton doneBtn = tinyButton("Done");
                doneBtn.setBackground(COLOR_COMPLETED);
                doneBtn.addActionListener(e ->
                    apiClient.updateStatus(task.getId(), "COMPLETED",
                        t -> refreshCallback.run(), refreshCallback::run));
                actions.add(doneBtn);
            }
        }

        JButton delBtn = tinyButton("✕");
        delBtn.setBackground(COLOR_URGENT);
        delBtn.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                "Delete task \"" + task.getName() + "\"?",
                "Delete Task", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                apiClient.deleteTask(task.getId(), refreshCallback::run, refreshCallback::run);
            }
        });
        actions.add(delBtn);

        bottomRow.add(actions, BorderLayout.EAST);
        card.add(bottomRow, BorderLayout.SOUTH);

        return card;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(COLOR_PANEL_BG);
        footer.add(buildMemberBarContent(new ArrayList<>()), BorderLayout.CENTER);
        footer.add(buildSyncRow(), BorderLayout.SOUTH);
        return footer;
    }

    private JPanel buildMemberBar(List<GroupMember> members) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(COLOR_PANEL_BG);
        p.add(buildMemberBarContent(members));
        return p;
    }

    private JPanel buildMemberBarContent(List<GroupMember> members) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        bar.setBackground(COLOR_PANEL_BG);
        for (GroupMember m : members) {
            JLabel lbl = new JLabel(m.getUsername() + " (" + m.getTasksAssigned() + ")");
            lbl.setForeground(COLOR_SUBTEXT);
            lbl.setFont(lbl.getFont().deriveFont(10f));
            bar.add(lbl);
        }
        return bar;
    }

    private JPanel buildSyncRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        row.setBackground(COLOR_PANEL_BG);
        syncLabel = new JLabel("Last sync: --");
        syncLabel.setForeground(COLOR_SUBTEXT);
        syncLabel.setFont(syncLabel.getFont().deriveFont(9f));
        row.add(syncLabel);
        return row;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void toggleAddForm() {
        showingForm = !showingForm;
        addTaskForm.setVisible(showingForm);
        revalidate();
        repaint();
    }

    private void submitAddTask(ActionEvent e) {
        String name    = nameField.getText().trim();
        String skill   = (String) skillCombo.getSelectedItem();
        int qty        = (Integer) quantitySpinner.getValue();
        String assignee= (String) assigneeCombo.getSelectedItem();
        String priority= urgentToggle.isSelected() ? "URGENT" : "NORMAL";

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Task name cannot be empty.");
            return;
        }

        if ("Unassigned".equals(assignee)) assignee = null;

        final String finalAssignee = assignee;
        apiClient.createTask(name, skill, qty, finalAssignee, priority,
            task -> {
                SwingUtilities.invokeLater(() -> {
                    nameField.setText("");
                    quantitySpinner.setValue(1);
                    assigneeCombo.setSelectedIndex(0);
                    urgentToggle.setSelected(false);
                    urgentToggle.setText("Normal");
                    toggleAddForm();
                });
                refreshCallback.run();
            },
            () -> SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, "Failed to create task. Check config.")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Color statusColor(String status) {
        switch (status) {
            case "CLAIMED":     return COLOR_CLAIMED;
            case "IN_PROGRESS": return COLOR_IN_PROGRESS;
            case "COMPLETED":   return COLOR_COMPLETED;
            default:            return COLOR_UNASSIGNED;
        }
    }

    private JLabel badge(String text, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setOpaque(true);
        lbl.setBackground(color);
        lbl.setForeground(Color.WHITE);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 9f));
        lbl.setBorder(new EmptyBorder(1, 5, 1, 5));
        return lbl;
    }

    private JLabel subLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(COLOR_SUBTEXT);
        lbl.setFont(lbl.getFont().deriveFont(10f));
        return lbl;
    }

    private JButton styledButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(new Color(70, 70, 70));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 12f));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton tinyButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(new Color(80, 80, 80));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(btn.getFont().deriveFont(9f));
        btn.setMargin(new Insets(1, 4, 1, 4));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}

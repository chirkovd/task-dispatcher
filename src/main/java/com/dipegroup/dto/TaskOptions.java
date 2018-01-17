package com.dipegroup.dto;

import java.util.UUID;
import java.util.function.Function;

public class TaskOptions {

    private final String taskId;
    private String groupId;
    private Function<String, Runnable> callback;

    public TaskOptions(String taskId) {
        this.taskId = taskId;
    }

    public TaskOptions(TaskOptions groupOptions) {
        this.taskId = UUID.randomUUID().toString();
        this.groupId = groupOptions.getGroupId();
        this.callback = groupOptions.getCallback();
    }

    public String getTaskId() {
        return taskId;
    }

    public String getGroupId() {
        return groupId;
    }

    public TaskOptions setGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public Function<String, Runnable> getCallback() {
        return callback;
    }

    public TaskOptions setCallback(Function<String, Runnable> callback) {
        this.callback = callback;
        return this;
    }
}

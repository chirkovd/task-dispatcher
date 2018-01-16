package com.dipegroup.dto;

public class TaskInfo {

    private final String taskId;
    private String groupId;

    public TaskInfo(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
}

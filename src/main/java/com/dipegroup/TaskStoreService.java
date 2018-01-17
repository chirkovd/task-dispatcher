package com.dipegroup;

import com.dipegroup.dto.Task;
import com.dipegroup.dto.TaskInfo;
import com.dipegroup.dto.TaskOptions;
import com.dipegroup.store.InMemoryTaskStore;
import com.dipegroup.store.TaskStorage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;

public class TaskStoreService {

    private TaskStorage storage;

    public TaskStorage getStorage() {
        if (storage == null) {
            storage = new InMemoryTaskStore();
        }
        return storage;
    }

    public void setStorage(TaskStorage storage) {
        this.storage = storage;
    }

    public <E> TaskInfo storeTask(Future<E> future, TaskOptions options) {
        Task<E> task = new Task<>(future, options.getTaskId(), options.getCallback());

        TaskInfo taskInfo = task.getInfo();
        if (options.getGroupId() == null) {
            options.setGroupId("singleTask-" + options.getTaskId());
        }
        taskInfo.setGroupId(options.getGroupId());

        getStorage().store(task);
        return task.getInfo();
    }

    public Optional<Task> findTask(String taskId) {
        return getStorage().find(taskId);
    }

    public List<Task> findTasks(String groupId) {
        return getStorage().find(task -> Objects.equals(groupId, task.getInfo().getGroupId()));
    }

    public List<Task> findActiveTasks() {
        return getStorage().find(task -> !task.getFuture().isDone());
    }

    public List<Task> findCompletedTasks() {
        return getStorage().find(task -> task.getFuture().isDone());
    }

    public Optional<Task> deleteTask(String taskId) {
        return Optional.ofNullable(getStorage().delete(taskId));
    }
}

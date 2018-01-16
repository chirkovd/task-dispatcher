package com.dipegroup;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Function;

public class TaskStoreService {

    private final TaskStorage storage;

    public TaskStoreService(TaskStorage storage) {
        this.storage = storage;
    }

    public <E> TaskInfo storeTask(Future<E> future, String taskId, Function<String, Runnable> cancelJobFunction) {
        return storeTask(future, taskId, "singleTask-" + taskId, cancelJobFunction);
    }

    public <E> TaskInfo storeTask(Future<E> future, String taskId, String groupId,
                                  Function<String, Runnable> cancelJobFunction) {
        Task<E> task = new Task<>(future, taskId, cancelJobFunction);
        TaskInfo taskInfo = task.getInfo();
        taskInfo.setGroupId(groupId);
        storage.store(task);
        return task.getInfo();
    }

    public Optional<Task> findTask(String taskId) {
        return storage.find(taskId);
    }

    public List<Task> findTasks(String groupId) {
        return storage.find(task -> Objects.equals(groupId, task.getInfo().getGroupId()));
    }

    public List<Task> findActiveTasks() {
        return storage.find(task -> !task.getFuture().isDone());
    }

    public Optional<Task> deleteTask(String taskId) {
        return Optional.ofNullable(storage.delete(taskId));
    }
}

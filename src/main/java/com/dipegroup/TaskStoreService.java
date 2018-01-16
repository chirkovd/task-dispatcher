package com.dipegroup;

import com.dipegroup.dto.Task;
import com.dipegroup.dto.TaskInfo;
import com.dipegroup.store.InMemoryTaskStore;
import com.dipegroup.store.TaskStorage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Function;

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

    public <E> TaskInfo storeTask(Future<E> future, String taskId, Function<String, Runnable> cancelJobFunction) {
        return storeTask(future, taskId, "singleTask-" + taskId, cancelJobFunction);
    }

    public <E> TaskInfo storeTask(Future<E> future, String taskId, String groupId,
                                  Function<String, Runnable> cancelJobFunction) {
        Task<E> task = new Task<>(future, taskId, cancelJobFunction);
        TaskInfo taskInfo = task.getInfo();
        taskInfo.setGroupId(groupId);
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

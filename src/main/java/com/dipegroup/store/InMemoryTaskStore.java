package com.dipegroup.store;

import com.dipegroup.dto.Task;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class InMemoryTaskStore implements TaskStorage {

    private static final Map<String, Task> TASK_STORE = new ConcurrentHashMap<>();

    @Override
    public void store(Task task) {
        TASK_STORE.put(task.getInfo().getTaskId(), task);
    }

    @Override
    public Optional<Task> find(String taskId) {
        return Optional.ofNullable(TASK_STORE.get(taskId));
    }

    @Override
    public List<Task> find(Predicate<Task> predicate) {
        return TASK_STORE.values().stream().filter(predicate).collect(Collectors.toList());
    }

    @Override
    public Task delete(String taskId) {
        return TASK_STORE.remove(taskId);
    }
}

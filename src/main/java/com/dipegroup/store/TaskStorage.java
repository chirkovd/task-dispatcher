package com.dipegroup.store;

import com.dipegroup.dto.Task;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public interface TaskStorage {

    void store(Task task);

    void store(List<Task> tasks);

    Optional<Task> find(String taskId);

    List<Task> find(Predicate<Task> predicate);

    Task delete(String taskId);

}

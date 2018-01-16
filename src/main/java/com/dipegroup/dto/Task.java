package com.dipegroup.dto;

import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Function;

public class Task<E> {

    private final Future<E> future;
    private final TaskInfo info;
    private final Runnable callbackJob;

    public Task(Future<E> future, String taskId, Function<String, Runnable> cancelJobFunction) {
        this.future = future;
        this.info = new TaskInfo(taskId);
        this.callbackJob = Optional.ofNullable(cancelJobFunction)
                .map(f -> f.apply(taskId)).orElse(null);
    }

    public Future<E> getFuture() {
        return future;
    }

    public TaskInfo getInfo() {
        return info;
    }

    public void runCallback() {
        Optional.ofNullable(callbackJob).ifPresent(Runnable::run);
    }
}

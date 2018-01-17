package com.dipegroup.dto;

import java.util.Optional;
import java.util.concurrent.Future;

public class Task<E> {

    private final Future<E> future;
    private final TaskInfo info;
    private final Runnable callbackJob;

    public Task(Future<E> future, TaskOptions options) {
        this.future = future;
        this.info = new TaskInfo(options.getTaskId());
        this.callbackJob = Optional.ofNullable(options.getCallback())
                .map(f -> f.apply(options.getTaskId())).orElse(null);
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

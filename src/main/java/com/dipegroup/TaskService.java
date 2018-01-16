package com.dipegroup;

import com.dipegroup.dto.Task;
import com.dipegroup.dto.TaskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);

    private final ExecutorService executorService;
    private final TaskStoreService storeService;

    public TaskService(ExecutorService executorService, TaskStoreService storeService) {
        this.executorService = executorService;
        this.storeService = storeService;
    }

    public <E> TaskInfo perform(Callable<E> callable) {
        return perform(callable, UUID.randomUUID().toString());
    }

    public <E> TaskInfo perform(Callable<E> callable, Function<String, Runnable> cancelJob) {
        return perform(callable, UUID.randomUUID().toString(), cancelJob);
    }

    public <E> TaskInfo perform(Callable<E> callable, String taskId) {
        return storeService.storeTask(executorService.submit(wrapCallable(callable, taskId)), taskId, null);
    }

    public <E> TaskInfo perform(Callable<E> callable, String taskId, Function<String, Runnable> cancelJob) {
        return storeService.storeTask(executorService.submit(wrapCallable(callable, taskId)), taskId, cancelJob);
    }

    public <E> TaskInfo perform(Callable<E> callable, String taskId, String groupId) {
        return storeService.storeTask(executorService.submit(wrapCallable(callable, taskId)), taskId, groupId, null);
    }

    public <E> TaskInfo perform(Callable<E> callable, String taskId, String groupId,
                                Function<String, Runnable> cancelJob) {
        return storeService.storeTask(executorService
                .submit(wrapCallable(callable, taskId)), taskId, groupId, cancelJob);
    }

    public <E> List<TaskInfo> perform(List<Callable<E>> callableTasks) {
        String groupId = UUID.randomUUID().toString();
        return callableTasks.stream().map(callable -> perform(callable, UUID.randomUUID().toString(), groupId))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public <E> E result(String taskId) {
        Task task = storeService.findTask(taskId)
                .orElseThrow(() -> new IllegalArgumentException("task with " + taskId + " is not found"));

        E result = null;
        try {
            result = (E) task.getFuture().get();
        } catch (InterruptedException | CancellationException e) {
            logger.debug("Task " + taskId + " was interrupted or canceled", e);
        } catch (ExecutionException e) {
            logger.debug("Task " + taskId + " returned unsuccessful result due to error", e);
        } finally {
            completeTask(task);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public <E> E result(String taskId, long timeout, TimeUnit unit) throws TimeoutException {
        Task task = storeService.findTask(taskId)
                .orElseThrow(() -> new IllegalArgumentException("task with " + taskId + " is not found"));

        boolean isTimeout = false;
        E result = null;
        try {
            result = (E) task.getFuture().get(timeout, unit);
        } catch (InterruptedException | CancellationException e) {
            logger.debug("Task " + taskId + " was interrupted or canceled", e);
        } catch (ExecutionException e) {
            logger.debug("Task " + taskId + " returned unsuccessful result due to error", e);
        } catch (TimeoutException e) {
            logger.debug("Task " + taskId + " is in progress", e);
            isTimeout = true;
            throw e;
        } finally {
            if (!isTimeout) {
                completeTask(task);
            }
        }
        return result;
    }


    public boolean exist(String taskId) {
        return storeService.findTask(taskId).isPresent();
    }

    public void cancel(String taskId) {
        storeService.findTask(taskId).ifPresent(this::cancelTask);
    }

    public void cancelGroup(String groupId) {
        storeService.findTasks(groupId).forEach(this::cancelTask);
    }

    private <E> Callable<E> wrapCallable(Callable<E> callable, String taskId) {
        return () -> {
            try {
                return callable.call();
            } catch (Exception e) {
                logger.debug("Error acquired during task " + taskId + " execution", e);
                return null;
            }
        };
    }

    private <E> void cancelTask(Task<E> task) {
        task.getFuture().cancel(true);
        task.runCancelJob();
    }

    private <E> void completeTask(Task<E> task) {
        logger.debug("Delete task {} from store", task.getInfo().getTaskId());
        storeService.deleteTask(task.getInfo().getTaskId()).ifPresent(Task::runCancelJob);
    }
}

package com.dipegroup;

import com.dipegroup.dto.Task;
import com.dipegroup.dto.TaskInfo;
import com.dipegroup.dto.TaskOptions;
import com.dipegroup.exceptions.TaskDispatcherException;
import com.dipegroup.reject.LoggingRejectResultServiceIml;
import com.dipegroup.reject.RejectResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);

    private final ExecutorService executorService;
    private final TaskStoreService storeService;

    private RejectResultService rejectResultService;

    public TaskService(ExecutorService executorService, TaskStoreService storeService) {
        this.executorService = executorService;
        this.storeService = storeService;
    }

    public RejectResultService getRejectResultService() {
        if (rejectResultService == null) {
            rejectResultService = new LoggingRejectResultServiceIml();
        }
        return rejectResultService;
    }

    public void setRejectResultService(RejectResultService rejectResultService) {
        this.rejectResultService = rejectResultService;
    }

    public <E> TaskInfo perform(Callable<E> callable) {
        return perform(callable, new TaskOptions(UUID.randomUUID().toString()));
    }

    public <E> TaskInfo perform(Callable<E> callable, TaskOptions options) {
        return storeService.storeTask(executorService.submit(wrapCallable(callable, options.getTaskId())), options);
    }

    public <E> List<TaskInfo> perform(List<Callable<E>> callableTasks) {
        return perform(callableTasks, UUID.randomUUID().toString());
    }

    public <E> List<TaskInfo> perform(List<Callable<E>> callableTasks, String groupId) {
        return callableTasks.stream().map(callable -> perform(callable,
                new TaskOptions(UUID.randomUUID().toString()).setGroupId(groupId))).collect(Collectors.toList());
    }

    public <E> List<TaskInfo> perform(List<Callable<E>> callableTasks, TaskOptions options) {
        return callableTasks.stream()
                .map(callable -> perform(callable, new TaskOptions(options)))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public <E> E result(String taskId) throws TaskDispatcherException {
        Task task = storeService.findTask(taskId)
                .orElseThrow(() -> new TaskDispatcherException("task with " + taskId + " is not found"));

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
    public <E> E result(String taskId, long timeout, TimeUnit unit) throws TaskDispatcherException {
        Task task = storeService.findTask(taskId)
                .orElseThrow(() -> new TaskDispatcherException("task with " + taskId + " is not found"));

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
            throw new TaskDispatcherException("Cannot fetch result for task " + taskId, e);
        } finally {
            if (!isTimeout) {
                completeTask(task);
            }
        }
        return result;
    }

    public <E> Map<String, E> merge(String groupId) {
        return storeService.findTasks(groupId).stream().map(task -> task.getInfo().getTaskId())
                .collect(HashMap::new, (map, taskId) -> {
                    E r = null;
                    try {
                        r = result(taskId);
                    } catch (TaskDispatcherException e) {
                        logger.debug("Cannot fetch result for task " + taskId, e);
                    }
                    map.put(taskId, r);
                }, HashMap::putAll);
    }

    public <E> Map<String, E> merge(String groupId, long timeout, TimeUnit unit) {
        return storeService.findTasks(groupId).stream().map(task -> task.getInfo().getTaskId())
                .collect(HashMap::new, (map, taskId) -> {
                    E r = null;
                    try {
                        r = result(taskId, timeout, unit);
                    } catch (TaskDispatcherException e) {
                        logger.debug("Cannot fetch result for task " + taskId, e);
                    }
                    map.put(taskId, r);
                }, HashMap::putAll);
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
                getRejectResultService().handle(e, taskId);
                return null;
            }
        };
    }

    private <E> void cancelTask(Task<E> task) {
        task.getFuture().cancel(true);
        completeTask(task);
    }

    private <E> void completeTask(Task<E> task) {
        String taskId = task.getInfo().getTaskId();
        storeService.deleteTask(taskId).ifPresent(t -> {
            logger.debug("Task {} was deleted from store", taskId);
            t.runCallback();
        });
    }
}

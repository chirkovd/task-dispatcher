package com.dipegroup;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TaskServiceTest {

    private static TaskStoreService storeService;
    private static TaskService taskService;

    @BeforeAll
    public static void setUp() {
        storeService = new TaskStoreService(new InMemoryTaskStore());
        taskService = new TaskService(Executors.newFixedThreadPool(5), storeService);
    }

    @AfterAll
    public static void tearDown() {
        assertTrue(storeService.findActiveTasks().isEmpty(), "All started jobs should be deleted from the store");
    }

    @Test
    void testSingleLongTask() {
        int count = 0;
        AtomicInteger counter = new AtomicInteger(count);

        TaskInfo info = taskService.perform(() -> {
            Thread.sleep(1000);
            return counter.incrementAndGet();
        });

        assertNotNull(info, "Task info should be returned by task service");

        int result = taskService.result(info.getTaskId());
        assertEquals(count + 1, result);

        assertFalse(taskService.exist(info.getTaskId()), "Task should be deleted from store automatically");
    }

    @Test
    void testMultipleLongTasks() {
        AtomicInteger counter = new AtomicInteger(0);

        List<Callable<Integer>> tasks = new ArrayList<>();
        tasks.add(() -> {
            Thread.sleep(1000);
            return counter.incrementAndGet();
        });
        tasks.add(() -> {
            Thread.sleep(2000);
            return counter.incrementAndGet();
        });
        List<TaskInfo> info = taskService.perform(tasks);
        assertEquals(2, info.size(), "There should be 2 started jobs");
        TaskInfo firstTask = info.get(0);
        TaskInfo secondTask = info.get(1);

        assertEquals(firstTask.getGroupId(), secondTask.getGroupId(), "All tasks should have the same groupId");
        taskService.result(firstTask.getTaskId());
        taskService.result(secondTask.getTaskId());

        assertEquals(2, counter.get(), "Result should be 2");
    }

    @Test
    void testTaskCancel() {
        AtomicInteger counter = new AtomicInteger(0);
        TaskInfo info = taskService.perform(() -> {
            Thread.sleep(2000);
            return counter.incrementAndGet();
        });

        TaskInfo resultInfo = taskService.perform(() -> taskService.result(info.getTaskId()));

        taskService.cancel(info.getTaskId());

        assertNull(taskService.result(resultInfo.getTaskId()), "Canceled task should return null");

        assertFalse(taskService.exist(resultInfo.getTaskId()));
        assertFalse(taskService.exist(info.getTaskId()));
    }

    @Test
    void testTasksCancel() {
        AtomicInteger counter = new AtomicInteger(0);

        List<Callable<Integer>> tasks = new ArrayList<>();
        tasks.add(() -> {
            Thread.sleep(5000);
            return counter.incrementAndGet();
        });
        tasks.add(() -> {
            Thread.sleep(5000);
            return counter.incrementAndGet();
        });

        List<TaskInfo> info = taskService.perform(tasks);
        assertEquals(2, info.size(), "There should be 2 started jobs");
        TaskInfo firstTask = info.get(0);
        TaskInfo secondTask = info.get(1);

        TaskInfo resultInfo = taskService.perform(() -> {
            int result = 0;
            result += Optional.ofNullable(taskService.<Integer>result(firstTask.getTaskId())).orElse(0);
            result += Optional.ofNullable(taskService.<Integer>result(secondTask.getTaskId())).orElse(0);
            return result;
        });

        taskService.cancelGroup(firstTask.getGroupId());

        assertTrue(taskService.<Integer>result(resultInfo.getTaskId()) < 3,
                "Tasks should be canceled, counter should not be incremented");

        assertFalse(taskService.exist(resultInfo.getTaskId()));
        assertFalse(taskService.exist(firstTask.getTaskId()));
        assertFalse(taskService.exist(firstTask.getTaskId()));
    }

    @Test
    void testTaskWithError() {
        TaskInfo info = taskService.perform(() -> {
            Thread.sleep(1000);
            throw new IllegalArgumentException("some arg is not valid");
        });
        assertNull(taskService.result(info.getTaskId()));
        assertFalse(taskService.exist(info.getTaskId()),
                "Task should be deleted from store automatically after execution fail");
    }

    @Test
    void testLongTaskWithTimeout() throws TimeoutException {
        TaskInfo info = taskService.perform(() -> {
            Thread.sleep(2000);
            return 0;
        });
        assertNotNull(info, "Task info should be returned by task service");
        assertThrows(TimeoutException.class, () -> taskService.result(info.getTaskId(), 1, TimeUnit.SECONDS));
        assertEquals(0, taskService.<Integer>result(info.getTaskId(), 1500, TimeUnit.MILLISECONDS).intValue());
        assertFalse(taskService.exist(info.getTaskId()));
    }

    @Test
    void testTaskWithCallback() {
        Map<String, AtomicInteger> externalJobs = new HashMap<>();

        String commandId = String.valueOf(System.currentTimeMillis());
        taskService.perform(() -> {
            externalJobs.put(commandId, new AtomicInteger(0));
            Thread.sleep(2000);
            return externalJobs.get(commandId).incrementAndGet();
        }, commandId, taskId -> () -> externalJobs.remove(taskId));

        assertEquals(1, taskService.<Integer>result(commandId).intValue());
        assertFalse(taskService.exist(commandId));
        assertTrue(externalJobs.isEmpty());
    }


}
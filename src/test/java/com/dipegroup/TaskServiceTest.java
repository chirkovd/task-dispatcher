package com.dipegroup;

import com.dipegroup.dto.TaskInfo;
import com.dipegroup.dto.TaskOptions;
import com.dipegroup.exceptions.TaskDispatcherException;
import com.dipegroup.reject.ReThrowingErrorRejectResultServiceIml;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TaskServiceTest {

    private static TaskStoreService storeService;
    private static TaskService taskService;

    @BeforeAll
    public static void setUp() {
        storeService = new TaskStoreService();
        taskService = new TaskService(Executors.newFixedThreadPool(5), storeService);
        taskService.setRejectResultService(new ReThrowingErrorRejectResultServiceIml());
    }

    @AfterAll
    public static void tearDown() {
        assertTrue(storeService.findActiveTasks().isEmpty(), "All started jobs should be deleted from the store");
        assertTrue(storeService.findCompletedTasks().isEmpty(), "All completed jobs should be deleted from the store");
    }

    @Test
    public void testSingleLongTask() throws TaskDispatcherException {
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
    public void testMultipleLongTasks() throws TaskDispatcherException {
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
    public void testTaskCancel() throws TaskDispatcherException {
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
    public void testTasksCancel() throws TaskDispatcherException {
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
            Thread.sleep(1000);
            int result = 0;
            result += Optional.ofNullable(taskService.<Integer>result(firstTask.getTaskId())).orElse(0);
            result += Optional.ofNullable(taskService.<Integer>result(secondTask.getTaskId())).orElse(0);
            return result;
        });

        taskService.cancelGroup(firstTask.getGroupId());

        assertNull(taskService.<Integer>result(resultInfo.getTaskId()),
                "Tasks should be canceled, counter should not be incremented");

        assertFalse(taskService.exist(resultInfo.getTaskId()));
        assertFalse(taskService.exist(firstTask.getTaskId()));
        assertFalse(taskService.exist(firstTask.getTaskId()));
    }

    @Test
    public void testTaskWithError() throws TaskDispatcherException {
        TaskInfo info = taskService.perform(() -> {
            Thread.sleep(1000);
            throw new IllegalArgumentException("some arg is not valid");
        });
        assertNull(taskService.result(info.getTaskId()));
        assertFalse(taskService.exist(info.getTaskId()),
                "Task should be deleted from store automatically after execution fail");
    }

    @Test
    public void testLongTaskWithTimeout() throws TaskDispatcherException {
        TaskInfo info = taskService.perform(() -> {
            Thread.sleep(2000);
            return 0;
        });
        assertNotNull(info, "Task info should be returned by task service");
        assertThrows(TaskDispatcherException.class, () -> taskService.result(info.getTaskId(), 1, TimeUnit.SECONDS));
        assertEquals(0, taskService.<Integer>result(info.getTaskId(), 1500, TimeUnit.MILLISECONDS).intValue());
        assertFalse(taskService.exist(info.getTaskId()));
    }

    @Test
    public void testLongFailedTaskWithTimeout() throws TaskDispatcherException {
        AtomicBoolean atomicBoolean = new AtomicBoolean(false);

        TaskOptions options = new TaskOptions(UUID.randomUUID().toString())
                .setCallback(taskId -> () -> atomicBoolean.set(true));
        TaskInfo info = taskService.perform(() -> {
            Thread.sleep(1000);
            throw new IllegalArgumentException("Cannot complete task");
        }, options);

        assertNull(taskService.result(info.getTaskId(), 2, TimeUnit.SECONDS));
        assertTrue(atomicBoolean.get());
    }

    @Test
    public void testLongTaskCancelWithTimeout() throws TaskDispatcherException {
        TaskInfo info = taskService.perform(() -> {
            Thread.sleep(3000);
            return 0;
        });
        TaskInfo result = taskService.perform(() -> taskService.result(info.getTaskId(), 2, TimeUnit.SECONDS));
        taskService.cancel(info.getTaskId());

        assertFalse(taskService.exist(info.getTaskId()));
        assertNull(taskService.result(result.getTaskId()));
    }

    @Test
    public void testTaskWithCallback() throws TaskDispatcherException {
        Map<String, AtomicInteger> externalJobs = new HashMap<>();

        String commandId = String.valueOf(System.currentTimeMillis());
        TaskOptions options = new TaskOptions(commandId)
                .setCallback(taskId -> () -> externalJobs.remove(taskId));

        taskService.perform(() -> {
            externalJobs.put(commandId, new AtomicInteger(0));
            Thread.sleep(2000);
            return externalJobs.get(commandId).incrementAndGet();
        }, options);

        assertEquals(1, taskService.<Integer>result(commandId).intValue());
        assertFalse(taskService.exist(commandId));
        assertTrue(externalJobs.isEmpty());
    }

    @Test
    public void testFailedTaskWithId() throws TaskDispatcherException {
        String commandId = String.valueOf(System.currentTimeMillis());
        TaskOptions options = new TaskOptions(commandId);
        taskService.perform(() -> {
            Thread.sleep(2000);
            throw new IllegalArgumentException("Cannot proceed task with id " + commandId);
        }, options);
        assertNull(taskService.result(commandId));
    }

    @Test
    public void testTasksWithCancelFunction() throws InterruptedException {
        Map<String, AtomicInteger> externalJobs = new HashMap<>();

        String groupId = String.valueOf(System.currentTimeMillis());
        int jobs = ThreadLocalRandom.current().nextInt(5, 10);

        CountDownLatch countDownLatch = new CountDownLatch(jobs);

        for (int i = 0; i < jobs; i++) {
            String commandId = "task-id-" + i;

            TaskOptions options = new TaskOptions(commandId)
                    .setGroupId(groupId).setCallback(taskId -> () -> externalJobs.remove(taskId));
            taskService.perform(() -> {
                externalJobs.put(commandId, new AtomicInteger(0));
                try {
                    Thread.sleep(2000);
                } finally {
                    countDownLatch.countDown();
                }
                return externalJobs.get(commandId).incrementAndGet();
            }, options);
        }

        for (int i = 0; i < jobs; i++) {
            String commandId = "task-id-" + i;
            assertTrue(taskService.exist(commandId));
        }
        countDownLatch.await();

        taskService.cancelGroup(groupId);

        for (int i = 0; i < jobs; i++) {
            String commandId = "task-id-" + i;
            assertFalse(taskService.exist(commandId));
        }
    }

    @Test
    public void testMergeTasks() {
        String groupId = String.valueOf(System.currentTimeMillis());
        int jobs = ThreadLocalRandom.current().nextInt(5, 10);

        for (int i = 0; i < jobs; i++) {
            String commandId = "task-id-" + i;
            TaskOptions options = new TaskOptions(commandId).setGroupId(groupId);
            taskService.perform(() -> {
                Thread.sleep(2000);
                return commandId;
            }, options);
        }

        Map<String, String> result = taskService.merge(groupId);
        assertEquals(jobs, result.size());
        result.forEach((key, value) -> {
            assertEquals(key, value);
            assertFalse(taskService.exist(key));
        });
    }

    @Test
    public void testMergeTasksWithTimeout() {
        String groupId = String.valueOf(System.currentTimeMillis());
        int jobs = ThreadLocalRandom.current().nextInt(5, 10);

        for (int i = 0; i < jobs; i++) {
            String commandId = "task-id-" + i;

            TaskOptions options = new TaskOptions(commandId).setGroupId(groupId);
            taskService.perform(() -> {
                Thread.sleep(1000);
                return commandId;
            }, options);
        }

        Map<String, String> result = taskService.merge(groupId, 100, TimeUnit.MILLISECONDS);
        assertEquals(jobs, result.size());
        result.forEach((key, value) -> assertNull(value));

        result = taskService.merge(groupId, 2000, TimeUnit.MILLISECONDS);

        result.forEach((key, value) -> {
            assertEquals(key, value);
            assertFalse(taskService.exist(key));
        });
    }
}
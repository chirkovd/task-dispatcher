# task-dispatcher
[![BCH compliance](https://bettercodehub.com/edge/badge/chirkovd/task-dispatcher?branch=master)](https://bettercodehub.com/results/chirkovd/task-dispatcher)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/3a39797db167483f868ed790758c640c)](https://www.codacy.com/app/dchirkov.work/task-dispatcher?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=chirkovd/task-dispatcher&amp;utm_campaign=Badge_Grade)

Small async task executor service, that allow perform tasks in parallel, cancel them and retrieve results

## Getting Started

Create TaskService with required implementation of *TaskStore* and *RejectResultService* interfaces. By default *InMemoryTaskStore* and *LoggingRejectResultServiceIml* would be used.

```
    ExecutorService executor = Executors.newFixedThreadPool(5);
    TaskStoreService storeService = new TaskStoreService();
    TaskService taskService = new TaskService(executor, storeService);
```

## Run task

```
    TaskInfo info = taskService.perform(() -> {
        return <T> ... // result of long running task
    });
    
    T result = taskService.<T>result(info.getTaskId()); // or
    T result = taskService.<T>result(info.getTaskId(), 1, TimeUnit.MILLISECONDS);
```

Method 'result' automatically remove task from the store after loading result

## Run tasks

```
    List<Callable<T>> tasks = new ArrayList<>();
    ... // add required tasks to collection
    
    List<TaskInfo> info = taskService.perform(tasks);
```

Returned list *info* contains information about all started tasks (all task would have same *groupId* and auto generated UUID.randomUUID() *taskId*)
Task id and groupId can be specified manually

## Cancel task

```
    TaskInfo info = taskService.perform(() -> {
        return <T> ... // result of long running task
    });
    taskService.cancel(info.getTaskId());
``` 

## Cancel tasks

```
    List<Callable<T>> tasks = new ArrayList<>();
    ... // add required tasks to collection
    
    String groupId = "groupId";
    List<TaskInfo> info = taskService.perform(tasks, groupId);
    
    taskService.cancelGroup(groupId);
```

All tasks would be deleted from the store, all threads would be marked as canceled. If there is some code in the body of started tasks, that could throw *InterruptedException*, this exception would be thrown and handled properly by *TaskService*

## Merge results

```
    String groupId = "groupId";
    List<Callable<T>> tasks = new ArrayList<>();
    ... // add required tasks to collection
    
    Map<String, T> result = taskService.merge(groupId);
```
Started tasks results can be collected to the map

## Start task with callback function

```
    TaskInfo info = taskService.perform(() -> {
            return <T> ... // result of long running task
        }, taskId -> () -> {
            ... // execute some staff related to started task
        });
```

Callback function would be executed after calling *result* method.
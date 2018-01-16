package com.dipegroup.reject;

public interface RejectResultService {

    void handle(Exception e, String taskId) throws Exception;
}

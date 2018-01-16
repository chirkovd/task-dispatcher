package com.dipegroup.reject;

public class ReThrowingErrorRejectResultServiceIml implements RejectResultService {

    @Override
    public void handle(Exception e, String taskId) throws Exception {
        throw e;
    }
}

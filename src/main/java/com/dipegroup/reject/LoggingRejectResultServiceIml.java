package com.dipegroup.reject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingRejectResultServiceIml implements RejectResultService {

    private static final Logger logger = LoggerFactory.getLogger(LoggingRejectResultServiceIml.class);

    @Override
    public void handle(Exception e, String taskId) {
        logger.debug("Error acquired during task " + taskId + " execution", e);
    }
}

package com.griddynamics.jagger.monitoring;

import com.griddynamics.jagger.agent.model.SystemInfo;
import com.griddynamics.jagger.coordinator.NodeContext;
import com.griddynamics.jagger.coordinator.NodeId;
import com.griddynamics.jagger.storage.fs.logging.LogWriter;
import com.griddynamics.jagger.storage.fs.logging.MonitoringLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Created by kgribov on 3/11/14.
 */
public class OldLoggingMonitoringProcessor implements MonitoringProcessor {
    private Logger log = LoggerFactory.getLogger(OldLoggingMonitoringProcessor.class);

    public static final String MONITORING_MARKER = "MONITORING";

    private String groupId = "task-1";

    @Override
    public void process(String sessionId, String taskId, NodeId agentId, NodeContext nodeContext, SystemInfo systemInfo) {
        if (!taskId.equals(groupId)){
            return;
        }

        // monitor task id
        taskId = "task-3";

        nodeContext.getService(LogWriter.class).log(sessionId, taskId + File.separatorChar + MONITORING_MARKER,
                agentId.getIdentifier(), new MonitoringLogEntry(systemInfo));
        log.trace("System info {} received from agent {} and has been written to FileStorage", systemInfo, agentId);
    }
}

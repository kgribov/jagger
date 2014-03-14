package com.griddynamics.jagger.monitoring;

import com.griddynamics.jagger.agent.model.SystemInfo;
import com.griddynamics.jagger.coordinator.NodeContext;
import com.griddynamics.jagger.coordinator.NodeId;
import com.griddynamics.jagger.storage.fs.logging.LogWriter;
import com.griddynamics.jagger.storage.fs.logging.MonitoringLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.io.File;

/**
 * Created by kgribov on 3/14/14.
 */
public class OldMonitoringProcessor implements MonitoringProcessor {
    private Logger log = LoggerFactory.getLogger(LoggingMonitoringProcessor.class);
    private LogWriter logWriter;

    public static final String MONITORING_MARKER = "MONITORING";

    @Override
    public void process(String sessionId, String taskId, NodeId agentId, NodeContext nodeContext, SystemInfo systemInfo) {
        logWriter.log(sessionId, taskId + File.separatorChar + MONITORING_MARKER,
                agentId.getIdentifier(), new MonitoringLogEntry(systemInfo));
        log.trace("System info {} received from agent {} and has been written to FileStorage", systemInfo, agentId);
    }

    @Required
    public void setLogWriter(LogWriter logWriter) {
        this.logWriter = logWriter;
    }
}
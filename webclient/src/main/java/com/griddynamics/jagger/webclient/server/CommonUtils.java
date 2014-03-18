package com.griddynamics.jagger.webclient.server;

import com.griddynamics.jagger.agent.model.DefaultMonitoringParameters;
import com.griddynamics.jagger.monitoring.reporting.GroupKey;
import com.griddynamics.jagger.util.AgentUtils;
import com.griddynamics.jagger.webclient.client.dto.TaskDataDto;

import java.util.*;

/**
 * Created by kgribov on 3/17/14.
 */
public class CommonUtils {

    /** Filter metrics to find standard monitoring metrics and dismiss not used monitoring metrics
     *
     * @param origin - a list of such arrays: [(String)metricId, ... , ...]
     * @param monitoringPlotGroups - monitoringPlotGroups from reporting.conf.xml
     * @return metrics without unused monitoring metrics
     */
    public static List<Object[]> filterMonitoring(List<Object[]> origin, Map<GroupKey, DefaultMonitoringParameters[]> monitoringPlotGroups){
        List<Object[]> result = new ArrayList<Object[]>(origin.size());

        // monitoring ids in reporting
        Set<String> reportingMonitoring = new HashSet<String>();
        for (DefaultMonitoringParameters[] value : monitoringPlotGroups.values()){
            for (DefaultMonitoringParameters parameter : value){
                reportingMonitoring.add(parameter.getId());
            }
        }

        // all monitoring ids
        Set<String> allMonitoring = new HashSet<String>();
        for (DefaultMonitoringParameters parameter : DefaultMonitoringParameters.values()){
            allMonitoring.add(parameter.getId());
        }

        for (Object[] row : origin){
            String metricId = getMonitoringId((String)row[0]);
            if (allMonitoring.contains(metricId) && !reportingMonitoring.contains(metricId)){
                // ignore this metrics
                continue;
            }
            result.add(row);
        }

        return result;
    }

    private static String getMonitoringId(String origin){
        int splitIndex = origin.indexOf(AgentUtils.AGENT_NAME_SEPARATOR);
        if (splitIndex != -1){
            return origin.substring(0, splitIndex);
        }

        return origin;
    }


    /**
     * @param tests - a list of TaskDataDtos
     * @return a set of TaskDataDto's ids
     */
    public static Set<Long> getTestsIds(List<TaskDataDto> tests){
        Set<Long> taskIds = new HashSet<Long>();
        for (TaskDataDto tdd : tests) {
            taskIds.addAll(tdd.getIds());
        }
        return taskIds;
    }

    /** Return true if origin collection contains at least one element from another collection
     * @param origin - origin collection
     * @param elements - another collection
     */
    public static boolean containsAtLeastOne(Collection origin, Collection elements){
        boolean result = false;
        for (Object element : elements){
            if (origin.contains(element)){
                result = true;
                break;
            }
        }

        return result;
    }
}

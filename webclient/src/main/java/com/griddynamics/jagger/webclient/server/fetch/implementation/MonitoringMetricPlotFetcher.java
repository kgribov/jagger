package com.griddynamics.jagger.webclient.server.fetch.implementation;

import com.griddynamics.jagger.agent.model.DefaultMonitoringParameters;
import com.griddynamics.jagger.engine.e1.aggregator.session.model.TaskData;
import com.griddynamics.jagger.engine.e1.aggregator.workload.model.WorkloadData;
import com.griddynamics.jagger.monitoring.model.MonitoringStatistics;
import com.griddynamics.jagger.monitoring.model.PerformedMonitoring;
import com.griddynamics.jagger.monitoring.reporting.GroupKey;
import com.griddynamics.jagger.util.MonitoringIdUtils;
import com.griddynamics.jagger.util.Pair;
import com.griddynamics.jagger.webclient.client.dto.MetricNameDto;
import com.griddynamics.jagger.webclient.client.dto.PlotDatasetDto;
import com.griddynamics.jagger.webclient.client.dto.PlotSeriesDto;
import com.griddynamics.jagger.webclient.client.dto.PointDto;
import com.griddynamics.jagger.webclient.server.ColorCodeGenerator;
import com.griddynamics.jagger.webclient.server.DataProcessingUtil;
import com.griddynamics.jagger.webclient.server.fetch.PlotsDbMetricDataFetcher;
import org.springframework.beans.factory.annotation.Required;

import javax.persistence.NoResultException;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class MonitoringMetricPlotFetcher extends PlotsDbMetricDataFetcher {

    private Map<GroupKey, DefaultMonitoringParameters[]> monitoringPlotGroups;

    @Required
    public void setMonitoringPlotGroups(Map<GroupKey, DefaultMonitoringParameters[]> monitoringPlotGroups) {
        this.monitoringPlotGroups = monitoringPlotGroups;
    }

    @Override
    protected Set<Pair<MetricNameDto, List<PlotDatasetDto>>> fetchData(List<MetricNameDto> metricNames) {

        if (metricNames.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Pair<MetricNameDto, List<PlotDatasetDto>>> resultSet = new HashSet<Pair<MetricNameDto, List<PlotDatasetDto>>>(metricNames.size());
        for (MetricNameDto metricNameDto : metricNames) {

            Set<Long> taskIds = metricNameDto.getTaskIds();

            checkNotNull(taskIds, "taskIds is null");
            checkArgument(!taskIds.isEmpty(), "taskIds is empty");
            checkNotNull(metricNameDto, "metricNameDto is null");

            String monitoringKey;
            String agentIdentifier;
            String metricId =  metricNameDto.getMetricName();
            MonitoringIdUtils.MonitoringId monitoringId = MonitoringIdUtils.splitMonitoringMetricId(metricId);
            if (monitoringId != null) {
                monitoringKey = monitoringId.getMonitoringName();
                agentIdentifier = monitoringId.getAgentName();
            }
            else {
                log.error("Unable to split name '{}' to monitoringKey and agentIdentifier",metricId);
                throw new RuntimeException("Unable to split name '" + metricId + "' to monitoringKey and agentIdentifier");
            }

            DefaultMonitoringParameters[] defaultMonitoringParametersGroup = findDefaultMonitoringParameters(monitoringPlotGroups, monitoringKey);
            List<String> monitoringParametersList = assembleDefaultMonitoringParametersDescriptions(defaultMonitoringParametersGroup);
            log.debug("For plot {} there are exist {} monitoring parameters", metricNameDto, defaultMonitoringParametersGroup);

            Map<String, Map<String, List<MonitoringStatistics>>> finalComposedMap = new HashMap<String, Map<String, List<MonitoringStatistics>>>();
            for (Long taskId : taskIds) {
                // todo get all TaskData entities in one step.time safe.
                TaskData workloadTaskData = entityManager.find(TaskData.class, taskId);

                WorkloadData workloadData = findWorkloadDataBySessionIdAndTaskId(workloadTaskData.getSessionId(), workloadTaskData.getTaskId());

                // metricNameDto can contain taskId for tasks without monitoring data available => ignore
                try {
                    TaskData monitoringTaskData = findMonitoringTaskDataBySessionIdAndParentId(workloadData.getSessionId(), workloadData.getParentId());

                    // todo here we fetching all MonitoringStatistic entity with TaskData in it. may be it could be simplified.
                    List<MonitoringStatistics> monitoringStatisticsList = findAllMonitoringStatisticsByMonitoringTaskDataAndDescriptionInList(monitoringTaskData, monitoringParametersList, agentIdentifier);

                    Map<String, Map<String, List<MonitoringStatistics>>> composedMap = composeByBoxIdentifierAndDescription(monitoringStatisticsList, true);

                    for (Map.Entry<String, Map<String, List<MonitoringStatistics>>> boxEntry : composedMap.entrySet()) {
                        if (!finalComposedMap.containsKey(boxEntry.getKey())) {
                            finalComposedMap.put(boxEntry.getKey(), new HashMap<String, List<MonitoringStatistics>>());
                        }

                        for (Map.Entry<String, List<MonitoringStatistics>> descrEntry : boxEntry.getValue().entrySet()) {
                            if (!finalComposedMap.get(boxEntry.getKey()).containsKey(descrEntry.getKey())) {
                                finalComposedMap.get(boxEntry.getKey()).put(descrEntry.getKey(), new ArrayList<MonitoringStatistics>());
                            }
                            finalComposedMap.get(boxEntry.getKey()).get(descrEntry.getKey()).addAll(descrEntry.getValue());
                        }
                    }
                }
                catch (NoResultException ex) {
                    log.warn("Not able to fetch monitoring data for session id: " + workloadData.getSessionId() + " task id: " + taskId, ex);
                }
        }

            List<PlotSeriesDto> plotSeries = assemble(finalComposedMap, monitoringKey, taskIds);
            List<PlotDatasetDto> plotDatasetDtoList = new ArrayList<PlotDatasetDto>();
            for (PlotSeriesDto plotS : plotSeries) {
                plotDatasetDtoList.addAll(plotS.getPlotSeries());
            }
            resultSet.add(Pair.of(metricNameDto, plotDatasetDtoList));
        }
        return resultSet;
    }

    //============================
    //===========Auxiliary Methods
    //============================

    /**
     * Composed monitoring statistics to map: [ boxID -> [ description -> list of monitoringStatistics ] ]
     *
     * @param monitoringStatisticsList Monitoring Statistic to compose into Map
     * @return composed Map
     */
    protected Map<String, Map<String, List<MonitoringStatistics>>> composeByBoxIdentifierAndDescription(List<MonitoringStatistics> monitoringStatisticsList, boolean addSessionPrefix) {
        Map<String, Map<String, List<MonitoringStatistics>>> map = new HashMap<String, Map<String, List<MonitoringStatistics>>>();

        for (MonitoringStatistics monitoringStatistics : monitoringStatisticsList) {
            String boxIdentifier = monitoringStatistics.getBoxIdentifier() != null ? monitoringStatistics.getBoxIdentifier() : monitoringStatistics.getSystemUnderTestUrl();

            String description = legendProvider.generatePlotLegend(monitoringStatistics.getTaskData().getSessionId(), monitoringStatistics.getParameterId().getDescription(), addSessionPrefix);

            if (!map.containsKey(boxIdentifier)) {
                map.put(boxIdentifier, new HashMap<String, List<MonitoringStatistics>>());
            }
            Map<String, List<MonitoringStatistics>> descriptionsMap = map.get(boxIdentifier);
            if (!descriptionsMap.containsKey(description)) {
                descriptionsMap.put(description, new ArrayList<MonitoringStatistics>());
            }
            descriptionsMap.get(description).add(monitoringStatistics);
        }
        return map;
    }

    protected List<PlotSeriesDto> assemble(Map<String, Map<String, List<MonitoringStatistics>>> composedMap, String plotName, Set<Long> taskIds) {
        List<PlotSeriesDto> plotSeriesDtoList = new ArrayList<PlotSeriesDto>();
        for (Map.Entry<String, Map<String, List<MonitoringStatistics>>> entry : composedMap.entrySet()) {
            String boxIdentifier = entry.getKey();

            List<PlotDatasetDto> plotDatasetDtoList = new ArrayList<PlotDatasetDto>();
            for (Map.Entry<String, List<MonitoringStatistics>> boxEntry : entry.getValue().entrySet()) {
                String description = boxEntry.getKey();

                List<PointDto> pointDtoList = new ArrayList<PointDto>();
                for (MonitoringStatistics monitoringStatistics : boxEntry.getValue()) {
                    pointDtoList.add(new PointDto(DataProcessingUtil.round(monitoringStatistics.getTime() / 1000.0D), DataProcessingUtil.round(monitoringStatistics.getAverageValue())));
                }

                plotDatasetDtoList.add(new PlotDatasetDto(pointDtoList, description, ColorCodeGenerator.getHexColorCode()));
            }

            plotSeriesDtoList.add(new PlotSeriesDto(plotDatasetDtoList, "Time, sec", "", legendProvider.getPlotHeader(taskIds, plotName + " on " + boxIdentifier)));
        }

        return plotSeriesDtoList;
    }

    private DefaultMonitoringParameters[] findDefaultMonitoringParameters(Map<GroupKey, DefaultMonitoringParameters[]> monitoringPlotGroups, String plotName) {
        for (Map.Entry<GroupKey, DefaultMonitoringParameters[]> entry : monitoringPlotGroups.entrySet()) {
            for (DefaultMonitoringParameters defaultMonitoringParameters : entry.getValue()) {
                if (plotName.equals(defaultMonitoringParameters.getId())) {
                    DefaultMonitoringParameters[] result = {defaultMonitoringParameters};
                    return result;
                }
            }
        }

        throw new IllegalStateException("Appropriate defaultMonitoringParameters array is not found in monitoringPlotGroups for plot name: " + plotName);
    }

    private Map<String, WorkloadData> createIndexParentId2WorkloadData(List<WorkloadData> workloadDataList) {
        Map<String, WorkloadData> workloadDataMap = new HashMap<String, WorkloadData>();
        for (WorkloadData workloadData : workloadDataList) {
            workloadDataMap.put(workloadData.getParentId(), workloadData);
        }

        return workloadDataMap;
    }

    private Map<String, PerformedMonitoring> createIndexMonitoringId2PerformedMonitoring(List<PerformedMonitoring> performedMonitoringList) {
        Map<String, PerformedMonitoring> performedMonitoringMap = new HashMap<String, PerformedMonitoring>();
        for (PerformedMonitoring performedMonitoring : performedMonitoringList) {
            performedMonitoringMap.put(performedMonitoring.getMonitoringId(), performedMonitoring);
        }

        return performedMonitoringMap;
    }

    @SuppressWarnings("unchecked")
    private List<PerformedMonitoring> findAllPerformedMonitoringBySessionId(String sessionId) {

        return entityManager.createQuery("select pm from PerformedMonitoring as pm where pm.sessionId=:sessionId")
                .setParameter("sessionId", sessionId)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<WorkloadData> findAllWorkloadDataBySessionId(String sessionId) {
        return entityManager.createQuery("select wd from WorkloadData as wd where wd.sessionId=:sessionId order by wd.endTime asc")
                .setParameter("sessionId", sessionId)
                .getResultList();
    }

    private WorkloadData findWorkloadDataBySessionIdAndTaskId(String sessionId, String taskId) {
        return (WorkloadData) entityManager.createQuery("select wd from WorkloadData as wd where wd.sessionId=:sessionId and wd.taskId=:taskId")
                .setParameter("sessionId", sessionId)
                .setParameter("taskId", taskId)
                .getSingleResult();
    }

    private Date findSessionTasksStartTime(String sessionId) {
        return (Date) entityManager.createQuery("select min(wd.startTime) from WorkloadData as wd where wd.sessionId=:sessionId")
                .setParameter("sessionId", sessionId)
                .getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private List<MonitoringStatistics> findAllMonitoringStatisticsBySessionIdAndDescriptionInList(String sessionId, List<String> monitoringParametersList, String agentIdentifier) {
        return entityManager.createQuery("select ms from MonitoringStatistics as ms where sessionId=:sessionId " +
                "and ms.parameterId.description in (:descrList) and (ms.boxIdentifier=:agentIdentifier or ms.systemUnderTestUrl=:agentIdentifier) order by ms.taskData.number asc, ms.time asc")
                .setParameter("sessionId", sessionId)
                .setParameter("descrList", monitoringParametersList)
                .setParameter("agentIdentifier", agentIdentifier)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<MonitoringStatistics> findAllMonitoringStatisticsByMonitoringTaskDataAndDescriptionInList(TaskData monitoringTaskData, List<String> monitoringParametersList, String agentIdentifier) {
        return entityManager.createQuery("select ms from MonitoringStatistics as ms where ms.taskData = :monitoringTaskData " +
                "and ms.parameterId.description in (:descrList) and (ms.boxIdentifier=:agentIdentifier or ms.systemUnderTestUrl=:agentIdentifier)")
                .setParameter("monitoringTaskData", monitoringTaskData)
                .setParameter("descrList", monitoringParametersList)
                .setParameter("agentIdentifier", agentIdentifier)
                .getResultList();
    }

    private TaskData findMonitoringTaskDataBySessionIdAndParentId(String sessionId, String parentId) {
        return (TaskData) entityManager.createQuery(
                "select td from TaskData as td where td.sessionId=:sessionId and td.taskId=" +
                        "(select pm.monitoringId from PerformedMonitoring as pm where pm.sessionId=:sessionId and pm.parentId=:parentId)")
                .setParameter("sessionId", sessionId)
                .setParameter("parentId", parentId)
                .getSingleResult();
    }

    private List<String> assembleDefaultMonitoringParametersDescriptions(DefaultMonitoringParameters[] defaultMonitoringParametersGroup) {
        List<String> monitoringParametersList = new ArrayList<String>();
        for (DefaultMonitoringParameters defaultMonitoringParameter : defaultMonitoringParametersGroup) {
            monitoringParametersList.add(defaultMonitoringParameter.getDescription());
        }

        return monitoringParametersList;
    }
}

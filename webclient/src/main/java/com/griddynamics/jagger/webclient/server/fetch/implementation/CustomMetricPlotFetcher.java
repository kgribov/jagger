package com.griddynamics.jagger.webclient.server.fetch.implementation;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.griddynamics.jagger.util.Pair;
import com.griddynamics.jagger.webclient.client.dto.MetricNameDto;
import com.griddynamics.jagger.webclient.client.dto.PlotDatasetDto;
import com.griddynamics.jagger.webclient.client.dto.PointDto;
import com.griddynamics.jagger.webclient.server.ColorCodeGenerator;
import com.griddynamics.jagger.webclient.server.DataProcessingUtil;
import com.griddynamics.jagger.webclient.server.fetch.PlotsDbMetricDataFetcher;

import java.util.*;

public class CustomMetricPlotFetcher extends PlotsDbMetricDataFetcher {
    @Override
    protected Set<Pair<MetricNameDto, List<PlotDatasetDto>>> fetchData(List<MetricNameDto> metricNames) {

        if (metricNames.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Pair<MetricNameDto, List<PlotDatasetDto>>> resultSet = new HashSet<Pair<MetricNameDto, List<PlotDatasetDto>>>(metricNames.size());
        Set<Long> taskIds = new HashSet<Long>();
        Set<String> metricIds = new HashSet<String>();
        for (MetricNameDto metricNameDto : metricNames) {
            taskIds.addAll(metricNameDto.getTaskIds());
            metricIds.add(metricNameDto.getMetricName());
        }

        List<Object[]> rawAllData = getAllRawData(taskIds, metricIds);

        if (rawAllData.isEmpty()) {
            throw new RuntimeException("Could not find plot data for " + metricNames);
        }

        Map<Long, Multimap<String, Object[]>> taskIdMetricIdRawMap = createMappedPlotDatasets(metricNames);
        for (Object[] rawData : rawAllData) {
            Long taskDataId = (Long) rawData[0];
            String metricId = (String) rawData[4];
            taskIdMetricIdRawMap.get(taskDataId).put(metricId, rawData);
        }

        Multimap<MetricNameDto, PlotDatasetDto> metricNamePlotMap = ArrayListMultimap.create();

        for (MetricNameDto metricName : metricNames) {
            String displayName = metricName.getMetricDisplayName();
            for (Long taskId : metricName.getTaskIds()) {
                Collection<Object[]> rawData;

                try {
                    rawData = taskIdMetricIdRawMap.get(taskId).get(metricName.getMetricName());

                    if (rawData.isEmpty()) {
                        // no data was saved for given task Id and metric Id
                        continue;
                    }
                } catch (NullPointerException e) {
                    // we did not find metric with given TaskDataId
                    // it could happen if we got 2 sessions, with different sets of custom metrics
                    continue;
                }

                List<PointDto> points = new ArrayList<PointDto>(rawData.size());
                String sessionId = null;

                for (Object[] metricDetails : rawData){
                    if (sessionId == null) sessionId = (String)metricDetails[3];
                    points.add(new PointDto((Long)metricDetails[1] / 1000D, Double.parseDouble(metricDetails[2].toString())));
                }

                PlotDatasetDto plotDatasetDto = new PlotDatasetDto(points, legendProvider.generatePlotLegend(sessionId, displayName, true), ColorCodeGenerator.getHexColorCode());
                metricNamePlotMap.put(metricName, plotDatasetDto);
            }
        }

        for (MetricNameDto metricName : metricNamePlotMap.keySet()) {
            List<PlotDatasetDto> plotDatasetDtoList = new ArrayList<PlotDatasetDto>(metricNamePlotMap.get(metricName));
            resultSet.add(
                    Pair.of(
                            metricName,
                            plotDatasetDtoList
                    ));
        }

        return resultSet;
    }

    /**
     * @return collection of objects {Task Data id, time, value, sessionId, metricId}
     */
    protected List<Object[]> getAllRawData(Set<Long> taskIds, Set<String> metricIds) {

        List<Object[]> resultList = new ArrayList<Object[]>();

        resultList.addAll(getPlotDataNewModel(taskIds, metricIds));
        resultList.addAll(getPlotDataOldModel(taskIds, metricIds));
        return resultList;
    }

    /**
     * @return collection of objects {Task Data id, time, value, sessionId, metricId}
     */
    private Collection<? extends Object[]> getPlotDataOldModel(Set<Long> taskIds, Set<String> metricIds) {
        return entityManager.createQuery(
                "select metrics.taskData.id, metrics.time, metrics.value, metrics.taskData.sessionId, metrics.metric from MetricDetails metrics " +
                        "where metrics.metric in (:metricIds) and metrics.taskData.id in (:taskIds)")
                .setParameter("taskIds", taskIds)
                .setParameter("metricIds", metricIds)
                .getResultList();
    }

    /**
     * @return collection of objects {Task Data id, time, value, sessionId, metricId}
     */
    protected Collection<? extends Object[]> getPlotDataNewModel(Set<Long> taskIds, Set<String> metricIds) {
        try {
            return entityManager.createQuery(
                    "select mpe.metricDescription.taskData.id, mpe.time, mpe.value, mpe.metricDescription.taskData.sessionId, mpe.metricDescription.metricId from MetricPointEntity as mpe " +
                            "where mpe.metricDescription.taskData.id in (:taskIds) and mpe.metricDescription.metricId in (:metricIds)")
                    .setParameter("taskIds", taskIds)
                    .setParameter("metricIds", metricIds)
                    .getResultList();
        } catch (Exception e) {
            log.debug("Could not fetch metric plots from MetricPointEntity: ", DataProcessingUtil.getMessageFromLastCause(e));
            return Collections.emptyList();
        }
    }



    /**
     * Map Collection of MetricNameDto first of all by TaskDataDto id, after all by metric name (metric id)
     * @param metricNameDtos collection of MetricNameDto to be mapped
     * @return Map <TaskDataDto.id, Multimap<metricName, Object[]>>
     */
    private Map<Long, Multimap<String, Object[]>> createMappedPlotDatasets(Collection<MetricNameDto> metricNameDtos) {
        Map<Long, Multimap<String, Object[]>> taskIdMap = new HashMap<Long, Multimap<String, Object[]>>();
        for (MetricNameDto metricName : metricNameDtos) {
            Set<Long> ids = metricName.getTaskIds();
            for (Long id : ids) {
                if (!taskIdMap.containsKey(id)) {
                    taskIdMap.put(id, ArrayListMultimap.<String, Object[]>create());
                }
            }
        }
        return taskIdMap;
    }
}

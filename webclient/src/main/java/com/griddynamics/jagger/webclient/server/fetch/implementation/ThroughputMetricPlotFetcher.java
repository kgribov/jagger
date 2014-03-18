package com.griddynamics.jagger.webclient.server.fetch.implementation;

import com.griddynamics.jagger.webclient.client.dto.PlotDatasetDto;
import com.griddynamics.jagger.webclient.client.dto.PointDto;
import com.griddynamics.jagger.webclient.server.ColorCodeGenerator;
import com.griddynamics.jagger.webclient.server.DefaultWorkloadParameters;

import java.util.*;

import static com.griddynamics.jagger.webclient.server.DataProcessingUtil.round;

public class ThroughputMetricPlotFetcher extends StandardMetricPlotFetcher<ThroughputMetricPlotFetcher.ThroughputRawData> {

    @Override
    protected Iterable<? extends PlotDatasetDto> assemble(Collection<ThroughputRawData> rawData) {
        List<PointDto> pointDtoList = new ArrayList<PointDto>(rawData.size());

        String sessionId = rawData.iterator().next().getSessionId();

        for (ThroughputRawData raw : rawData) {
            double x = round(raw.getTime() / 1000.0D);
            double y = round(raw.getThroughput());
            pointDtoList.add(new PointDto(x, y));
        }

        String legend = legendProvider.generatePlotLegend(sessionId, DefaultWorkloadParameters.THROUGHPUT.getDescription(), true);
        return Arrays.asList(new PlotDatasetDto(pointDtoList, legend, ColorCodeGenerator.getHexColorCode()));
    }

    @Override
    protected List<ThroughputRawData> findRawDataByTaskData(Set<Long> taskIds) {
        @SuppressWarnings("all")
        List<Object[]> rawDataList = entityManager.createQuery(
                "select tis.time, tis.throughput, tis.taskData.id, tis.taskData.sessionId from TimeInvocationStatistics as tis " +
                        "where tis.taskData.id in (:taskIds)")
                .setParameter("taskIds", taskIds)
                .getResultList();

        List<ThroughputRawData> resultList = new ArrayList<ThroughputRawData>(rawDataList.size());

        for (Object[] objects : rawDataList) {

            String sessionId = (String) objects[3];
            Long taskDataId = (Long) objects[2];
            Long time = (Long) objects[0];
            Double throughput = (Double) objects[1];

            resultList.add(new ThroughputRawData(sessionId, taskDataId, time, throughput));
        }

        return resultList;
    }

    public static class ThroughputRawData implements StandardMetricPlotFetcher.StandardMetricRawData {

        private String sessionId;
        private Long taskDataId;
        private Long time;
        private Double throughput;

        public ThroughputRawData(String sessionId, Long taskDataId, Long time, Double throughput) {
            this.sessionId = sessionId;
            this.taskDataId = taskDataId;
            this.time = time;
            this.throughput = throughput;
        }

        @Override
        public Long getTaskDataId() {
            return taskDataId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public Long getTime() {
            return time;
        }

        public Double getThroughput() {
            return throughput;
        }
    }
}

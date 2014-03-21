package com.griddynamics.jagger.webclient.server.fetch.implementation;

import com.griddynamics.jagger.webclient.client.dto.PlotDatasetDto;
import com.griddynamics.jagger.webclient.client.dto.PointDto;
import com.griddynamics.jagger.webclient.server.ColorCodeGenerator;
import static com.griddynamics.jagger.webclient.server.DataProcessingUtil.round;
import com.griddynamics.jagger.webclient.server.DefaultWorkloadParameters;

import java.util.*;

public class LatencyMetricPlotFetcher extends StandardMetricPlotFetcher<LatencyMetricPlotFetcher.LatencyRawData> {


    @Override
    protected Iterable<? extends PlotDatasetDto> assemble(Collection<LatencyRawData> rawDataList) {

        List<PlotDatasetDto> plotDatasetDtoList = new ArrayList<PlotDatasetDto>(2);

        String sessionId = rawDataList.iterator().next().getSessionId();

        List<PointDto> pointDtoLatencyList = new ArrayList<PointDto>(rawDataList.size());
        List<PointDto> pointDtoLatencyStdDevList = new ArrayList<PointDto>(rawDataList.size());

        for (LatencyRawData rawData : rawDataList) {
            double x = round(rawData.getTime() / 1000.0D);
            double yLatency = round(rawData.getLatency());
            double yLatencyStdDev = round(rawData.getLatencyStdDev());
            pointDtoLatencyList.add(new PointDto(x, yLatency));
            pointDtoLatencyStdDevList.add(new PointDto(x, yLatencyStdDev));
        }

        String legend = legendProvider.generatePlotLegend(sessionId, DefaultWorkloadParameters.LATENCY.getDescription(), true);
        PlotDatasetDto plotDatasetDto = new PlotDatasetDto(pointDtoLatencyList, legend, ColorCodeGenerator.getHexColorCode());
        plotDatasetDtoList.add(plotDatasetDto);

        legend = legendProvider.generatePlotLegend(sessionId, DefaultWorkloadParameters.LATENCY_STD_DEV.getDescription(), true);
        plotDatasetDto = new PlotDatasetDto(pointDtoLatencyStdDevList, legend, ColorCodeGenerator.getHexColorCode());
        plotDatasetDtoList.add(plotDatasetDto);

        return plotDatasetDtoList;
    }

    @Override
    protected List<LatencyRawData> findRawDataByTaskData(Set<Long> taskIds) {

        @SuppressWarnings("all")
        List<Object[]> rawDataList =  entityManager.createQuery(
                "select tis.time, tis.latency, tis.latencyStdDev, tis.taskData.id, tis.taskData.sessionId " +
                        "from TimeInvocationStatistics as tis where tis.taskData.id in (:taskIds)")
                .setParameter("taskIds", taskIds)
                .getResultList();

        List<LatencyRawData> resultList = new ArrayList<LatencyRawData>(rawDataList.size());

        for (Object[] objects : rawDataList) {
            String sessionId = (String) objects[4];
            Long taskDataId = (Long) objects[3];
            Long time = (Long) objects[0];
            Double latency = (Double) objects[1];
            Double latencyStdDev = (Double) objects[2];

            resultList.add(new LatencyRawData(sessionId, taskDataId, time, latency, latencyStdDev));
        }

        return resultList;
    }


    public static class LatencyRawData implements StandardMetricPlotFetcher.StandardMetricRawData {

        private String sessionId;
        private Long taskDataId;
        private Long time;
        private Double latency;
        private Double latencyStdDev;

        public LatencyRawData(String sessionId, Long taskDataId, Long time, Double latency, Double latencyStdDev) {
            this.sessionId = sessionId;
            this.taskDataId = taskDataId;
            this.time = time;
            this.latency = latency;
            this.latencyStdDev = latencyStdDev;
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

        public Double getLatency() {
            return latency;
        }

        public Double getLatencyStdDev() {
            return latencyStdDev;
        }
    }
}
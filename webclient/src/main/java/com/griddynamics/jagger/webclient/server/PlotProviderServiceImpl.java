package com.griddynamics.jagger.webclient.server;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.griddynamics.jagger.util.Pair;
import com.griddynamics.jagger.webclient.client.PlotProviderService;
import com.griddynamics.jagger.webclient.client.components.control.model.MetricNode;
import com.griddynamics.jagger.webclient.client.dto.*;
import com.griddynamics.jagger.webclient.server.fetch.PlotsDbMetricDataFetcher;
import com.griddynamics.jagger.webclient.server.fetch.implementation.*;
import com.griddynamics.jagger.webclient.server.plot.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;


/**
 * @author "Artem Kirillov" (akirillov@griddynamics.com)
 * @since 5/30/12
 */
public class PlotProviderServiceImpl implements PlotProviderService {
    private static final Logger log = LoggerFactory.getLogger(PlotProviderServiceImpl.class);

    private DataPointCompressingProcessor compressingProcessor;
    private MonitoringPlotDataProvider monitoringPlotDataProvider;

    private ExecutorService threadPool;

    private ThroughputMetricPlotFetcher throughputMetricPlotFetcher;
    private LatencyMetricPlotFetcher latencyMetricPlotFetcher;
    private TimeLatencyPercentileMetricPlotFetcher timeLatencyPercentileMetricPlotFetcher;
    private CustomMetricPlotFetcher customMetricPlotFetcher;
    private CustomTestGroupMetricPlotFetcher customTestGroupMetricPlotFetcher;
    private MonitoringMetricPlotFetcher monitoringMetricPlotFetcher;



    private LegendProvider legendProvider;

    //==========Setters

    @Required
    public void setCompressingProcessor(DataPointCompressingProcessor compressingProcessor) {
        this.compressingProcessor = compressingProcessor;
    }

    @Required
    public void setMonitoringPlotDataProvider(MonitoringPlotDataProvider monitoringPlotDataProvider) {
        this.monitoringPlotDataProvider = monitoringPlotDataProvider;
    }

    @Required
    public void setLegendProvider(LegendProvider legendProvider) {
        this.legendProvider = legendProvider;
    }

    @Required
    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    @Required
    public void setThroughputMetricPlotFetcher(ThroughputMetricPlotFetcher throughputMetricPlotFetcher) {
        this.throughputMetricPlotFetcher = throughputMetricPlotFetcher;
    }

    @Required
    public void setLatencyMetricPlotFetcher(LatencyMetricPlotFetcher latencyMetricPlotFetcher) {
        this.latencyMetricPlotFetcher = latencyMetricPlotFetcher;
    }

    @Required
    public void setTimeLatencyPercentileMetricPlotFetcher(TimeLatencyPercentileMetricPlotFetcher timeLatencyPercentileMetricPlotFetcher) {
        this.timeLatencyPercentileMetricPlotFetcher = timeLatencyPercentileMetricPlotFetcher;
    }

    @Required
    public void setCustomMetricPlotFetcher(CustomMetricPlotFetcher customMetricPlotFetcher) {
        this.customMetricPlotFetcher = customMetricPlotFetcher;
    }

    public void setCustomTestGroupMetricPlotFetcher(CustomTestGroupMetricPlotFetcher customTestGroupMetricPlotFetcher) {
        this.customTestGroupMetricPlotFetcher = customTestGroupMetricPlotFetcher;
    }

    @Required
    public void setMonitoringMetricPlotFetcher(MonitoringMetricPlotFetcher monitoringMetricPlotFetcher) {
        this.monitoringMetricPlotFetcher = monitoringMetricPlotFetcher;
    }

    //===========================
    //===========Contract Methods
    //===========================

    @Override
    public Map<MetricNode, PlotSeriesDto> getPlotData(Set<MetricNode> plots) throws IllegalArgumentException{

        long temp = System.currentTimeMillis();

        final Multimap<PlotsDbMetricDataFetcher, MetricNameDto> fetchMap = ArrayListMultimap.create();

        for (MetricNode metricNode : plots) {
            for (MetricNameDto metricNameDto : metricNode.getMetricNameDtoList()) {
                switch (metricNameDto.getOrigin()) {
                    case METRIC:
                        fetchMap.put(customMetricPlotFetcher, metricNameDto);
                        break;
                    case TEST_GROUP_METRIC:
                        fetchMap.put(customTestGroupMetricPlotFetcher, metricNameDto);
                        break;
                    case MONITORING:
                        fetchMap.put(monitoringMetricPlotFetcher, metricNameDto);
                        break;
                    case LATENCY:
                        fetchMap.put(latencyMetricPlotFetcher, metricNameDto);
                        break;
                    case LATENCY_PERCENTILE:
                        fetchMap.put(timeLatencyPercentileMetricPlotFetcher, metricNameDto);
                        break;
                    case THROUGHPUT:
                        fetchMap.put(throughputMetricPlotFetcher, metricNameDto);
                        break;
                    default:  // if anything else
                        log.error("MetricNameDto with origin : {} appears in metric name list for plot retrieving ({})", metricNameDto.getOrigin(), metricNameDto);
                        throw new RuntimeException("Unable to get plot for metric " + metricNameDto.getMetricName() +
                                " with origin: " + metricNameDto.getOrigin());
                }
            }
        }

        Set<PlotsDbMetricDataFetcher> fetcherSet = fetchMap.keySet();

        List<Future<Set<Pair<MetricNameDto, List<PlotDatasetDto>>>>> futures = new ArrayList<Future<Set<Pair<MetricNameDto, List<PlotDatasetDto>>>>>();

        for (final PlotsDbMetricDataFetcher fetcher : fetcherSet) {
            futures.add(threadPool.submit(new Callable<Set<Pair<MetricNameDto, List<PlotDatasetDto>>>>() {

                @Override
                public Set<Pair<MetricNameDto, List<PlotDatasetDto>>> call() throws Exception {
                    return fetcher.getResult(new ArrayList<MetricNameDto>(fetchMap.get(fetcher)));
                }
            }));
        }

        Set<Pair<MetricNameDto, List<PlotDatasetDto>>>  resultSet = new HashSet<Pair<MetricNameDto, List<PlotDatasetDto>>>();

        try {
            for (Future<Set<Pair<MetricNameDto, List<PlotDatasetDto>>>> future : futures) {
                resultSet.addAll(future.get());
            }
        } catch (Throwable th) {
            log.error("Exception while plots retrieving", th);
            throw new RuntimeException("Exception while plots retrieving", th);
        }

        Multimap<MetricNode, PlotDatasetDto> tempMultiMap = ArrayListMultimap.create();

        for (Pair<MetricNameDto, List<PlotDatasetDto>> pair : resultSet) {
            for (MetricNode metricNode : plots) {
                if (metricNode.getMetricNameDtoList().contains(pair.getFirst())) {
                    tempMultiMap.putAll(metricNode, pair.getSecond());
                    break;
                }
            }
        }

        Map<MetricNode, PlotSeriesDto> result = new HashMap<MetricNode, PlotSeriesDto>();

        for (MetricNode metricNode : plots) {
            List<PlotDatasetDto> plotDatasetDtoList = new ArrayList<PlotDatasetDto>(tempMultiMap.get(metricNode));

            // Sort lines by legend
            Collections.sort(plotDatasetDtoList, new Comparator<PlotDatasetDto>() {
                @Override
                public int compare(PlotDatasetDto o1, PlotDatasetDto o2) {
                    String param1 = o1.getLegend();
                    String param2 = o2.getLegend();
                    int res = String.CASE_INSENSITIVE_ORDER.compare(param1,param2);
                    return (res != 0) ? res : param1.compareTo(param2);
                }
            });

            // at the moment all MetricNameDtos in MetricNode have same taskIds => it is valid to use first one for legend provider
            result.put(metricNode, new PlotSeriesDto(plotDatasetDtoList,"Time, sec", "",legendProvider.getPlotHeader(metricNode.getMetricNameDtoList().get(0).getTaskIds(), metricNode.getDisplayName())));
        }

        log.debug("Total time of plots retrieving : " + (System.currentTimeMillis() - temp));
        return result;
    }


    @Override
    public Map<SessionPlotNameDto, List<PlotSeriesDto>> getSessionScopePlotData(String sessionId, Collection<SessionPlotNameDto> plotNames) {
        long timestamp = System.currentTimeMillis();
        Map<SessionPlotNameDto, List<PlotSeriesDto>> resultMap = new HashMap<SessionPlotNameDto, List<PlotSeriesDto>>();

        SessionScopePlotDataProvider plotDataProvider = monitoringPlotDataProvider;

        for(SessionPlotNameDto plotName : plotNames) {
            log.debug("getPlotData was invoked with sessionId={} and metricName={}", sessionId, plotName);
            List<PlotSeriesDto> plotSeriesDtoList;

            try {
                plotSeriesDtoList = plotDataProvider.getPlotData(sessionId, plotName.getMetricName());
                log.info("getSessionScopePlotData(): {}", getFormattedLogMessage(plotSeriesDtoList, sessionId, plotName.getMetricName(), System.currentTimeMillis() - timestamp));
                for (PlotSeriesDto plotSeriesDto : plotSeriesDtoList) {
                    for (PlotDatasetDto plotDatasetDto : plotSeriesDto.getPlotSeries()) {
                        List<PointDto> pointDtoList = compressingProcessor.process(plotDatasetDto.getPlotData());
                        plotDatasetDto.getPlotData().clear();
                        plotDatasetDto.getPlotData().addAll(pointDtoList);
                    }
                }
                log.info("getSessionScopePlotData() after compressing: {}", getFormattedLogMessage(plotSeriesDtoList, sessionId, plotName.getMetricName(), System.currentTimeMillis() - timestamp));
            } catch (Exception e) {
                System.err.println(e);
                log.error("Error is occurred during plot data loading for sessionId=" + sessionId + ", metricName=" + plotName, e);
                throw new RuntimeException(e);
            }
            resultMap.put(plotName, plotSeriesDtoList);
        }

        return resultMap;
    }

    //===========================
    //==========Auxiliary Methods
    //===========================

    private String getFormattedLogMessage(List<PlotSeriesDto> plotSeriesDto, String id, String plotName, long millis) {
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("For id=")
                .append(id)
                .append(", plot name=\"")
                .append(plotName)
                .append("\" ")
                .append(plotSeriesDto.size())
                .append(" plots were found: ");
        for (PlotSeriesDto dto : plotSeriesDto) {
            logBuilder.append("\n* \"").append(dto.getPlotHeader()).append("\" {");

            int summaryPointsCount = 0;
            for (PlotDatasetDto plotDatasetDto : dto.getPlotSeries()) {
                summaryPointsCount += plotDatasetDto.getPlotData().size();
                logBuilder.append("\"")
                        .append(plotDatasetDto.getLegend())
                        .append("\" [")
                        .append(plotDatasetDto.getPlotData().size())
                        .append(" fetched data points], ");
            }
            logBuilder.append("} //Summary: ").append(summaryPointsCount).append(" points;");
            logBuilder.append("\nExecuted for ").append(millis).append(" ms");
        }

        return logBuilder.toString();
    }
}

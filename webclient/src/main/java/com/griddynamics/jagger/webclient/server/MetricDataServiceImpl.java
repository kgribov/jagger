package com.griddynamics.jagger.webclient.server;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.griddynamics.jagger.webclient.client.MetricDataService;
import com.griddynamics.jagger.webclient.client.dto.*;
import com.griddynamics.jagger.webclient.server.fetch.MetricDataFetcher;
import com.griddynamics.jagger.webclient.server.fetch.implementation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Created with IntelliJ IDEA.
 * User: kirilkadurilka
 * Date: 08.04.13
 * Time: 17:53
 * To change this template use File | Settings | File Templates.
 */
public class MetricDataServiceImpl implements MetricDataService {

    private static final Logger log = LoggerFactory.getLogger(MetricDataServiceImpl.class);
    private ExecutorService threadPool;

    private StandardMetricSummaryFetcher standardMetricSummaryFetcher;
    private DurationMetricSummaryFetcher durationMetricSummaryFetcher;
    private LatencyMetricSummaryFetcher latencyMetricDataFetcher;
    private CustomMetricSummaryFetcher customMetricSummaryFetcher;
    private ValidatorSummaryFetcher validatorSummaryFetcher;

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public void setStandardMetricSummaryFetcher(StandardMetricSummaryFetcher standardMetricSummaryFetcher) {
        this.standardMetricSummaryFetcher = standardMetricSummaryFetcher;
    }

    public void setDurationMetricSummaryFetcher(DurationMetricSummaryFetcher durationMetricSummaryFetcher) {
        this.durationMetricSummaryFetcher = durationMetricSummaryFetcher;
    }

    public void setLatencyMetricDataFetcher(LatencyMetricSummaryFetcher latencyMetricDataFetcher) {
        this.latencyMetricDataFetcher = latencyMetricDataFetcher;
    }

    public void setCustomMetricSummaryFetcher(CustomMetricSummaryFetcher customMetricSummaryFetcher) {
        this.customMetricSummaryFetcher = customMetricSummaryFetcher;
    }

    public void setValidatorSummaryFetcher(ValidatorSummaryFetcher validatorSummaryFetcher) {
        this.validatorSummaryFetcher = validatorSummaryFetcher;
    }

    @Override
    public List<MetricDto> getMetrics(List<MetricNameDto> metricNames) {

        long temp = System.currentTimeMillis();
        List<MetricDto> result = new ArrayList<MetricDto>(metricNames.size());

        final Multimap<MetricDataFetcher<MetricDto>, MetricNameDto> fetchMap = ArrayListMultimap.create();

        for (MetricNameDto metricName : metricNames){
            switch (metricName.getOrigin()) {
                case STANDARD_METRICS:
                    fetchMap.put(standardMetricSummaryFetcher, metricName);
                    break;
                case DURATION:
                    fetchMap.put(durationMetricSummaryFetcher, metricName);
                    break;
                case LATENCY_PERCENTILE:
                    fetchMap.put(latencyMetricDataFetcher, metricName);
                    break;
                case METRIC:
                    fetchMap.put(customMetricSummaryFetcher, metricName);
                    break;
                case VALIDATOR:
                    fetchMap.put(validatorSummaryFetcher, metricName);
                    break;
                default:  // if anything else
                    log.error("MetricNameDto with origin : {} appears in metric name list for summary retrieving ({})", metricName.getOrigin(), metricName);
                    throw new RuntimeException("Unable to get summary data for metric " + metricName.getMetricName() +
                            " with origin: " + metricName.getOrigin());
            }
        }

        Set<MetricDataFetcher<MetricDto>> fetcherSet = fetchMap.keySet();

        List<Future<Set<MetricDto>>> futures = new ArrayList<Future<Set<MetricDto>>>();

        for (final MetricDataFetcher<MetricDto> fetcher : fetcherSet) {
            futures.add(threadPool.submit(new Callable<Set<MetricDto>>() {

                @Override
                public Set<MetricDto> call() throws Exception {
                    return fetcher.getResult(new ArrayList<MetricNameDto>(fetchMap.get(fetcher)));
                }
            }));
        }

        try {
            for (Future<Set<MetricDto>> future : futures) {
                result.addAll(future.get());
            }
        } catch (Throwable th) {
            th.printStackTrace();
            log.error("Exception while summary retrieving", th);
        }
        log.debug("{} ms spent for fetching summary data for {} metrics", System.currentTimeMillis() - temp, metricNames.size());

        return result;
    }
}
package com.griddynamics.jagger.webclient.server.fetch;


import com.griddynamics.jagger.webclient.client.dto.MetricNameDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Abstract class to fetch Data from list of MetricNameDto
 * @param <R>
 */
public abstract class MetricDataFetcher<R> {

    public abstract Set<R> getResult(List<MetricNameDto> metricNames);
}

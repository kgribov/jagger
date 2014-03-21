package com.griddynamics.jagger.webclient.server.fetch;

import com.griddynamics.jagger.util.Pair;
import com.griddynamics.jagger.webclient.client.dto.MetricNameDto;
import com.griddynamics.jagger.webclient.client.dto.PlotDatasetDto;
import com.griddynamics.jagger.webclient.server.LegendProvider;
import org.springframework.beans.factory.annotation.Required;

import java.util.List;


public abstract class PlotsDbMetricDataFetcher extends DbMetricDataFetcher<Pair<MetricNameDto, List<PlotDatasetDto>>> {

    protected LegendProvider legendProvider;

    @Required
    public void setLegendProvider(LegendProvider legendProvider) {
        this.legendProvider = legendProvider;
    }
}

package com.griddynamics.jagger.webclient.server.plot;

import com.griddynamics.jagger.webclient.client.dto.MetricNameDto;
import com.griddynamics.jagger.webclient.client.dto.PlotSeriesDto;

import java.util.List;

/**
 * @author "Artem Kirillov" (akirillov@griddynamics.com)
 * @since 5/31/12
 */
public interface PlotDataProvider {

    List<PlotSeriesDto> getPlotData(MetricNameDto metricNameDto);

}

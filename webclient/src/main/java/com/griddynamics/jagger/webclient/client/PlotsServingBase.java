package com.griddynamics.jagger.webclient.client;

import com.griddynamics.jagger.webclient.client.components.control.model.MetricNode;
import com.griddynamics.jagger.webclient.client.dto.MetricNameDto;

/**
 * @author "Artem Kirillov" (akirillov@griddynamics.com)
 * @since 6/19/12
 */
public abstract class PlotsServingBase {

    protected String generatePlotId(MetricNode metricNode) {
        return "#plot-" + metricNode.getId().toLowerCase().replaceAll("\\s+", "-");
    }

    protected String generateMetricPlotId(MetricNameDto metricNameDto) {
        return "" + metricNameDto.getTest().hashCode() + "#metric-scope-plot-" + metricNameDto.getMetricName().toLowerCase().replaceAll("\\s+", "-");
    }

    protected String generateSessionScopePlotId(String sessionId, String plotName) {
        return sessionId + "#session-scope-plot-" + plotName.toLowerCase().replaceAll("\\s+", "-");
    }

}

package com.griddynamics.jagger.webclient.server;

import com.griddynamics.jagger.webclient.client.dto.MetricNameDto;

/**
 * @author "Artem Kirillov" (akirillov@griddynamics.com)
 * @since 6/4/12
 */
public enum DefaultWorkloadParameters {
    LATENCY("Latency, sec", false, MetricNameDto.Origin.LATENCY),
    LATENCY_STD_DEV("Latency std dev, sec", false, MetricNameDto.Origin.LATENCY),
    THROUGHPUT("Throughput, tps", false, MetricNameDto.Origin.THROUGHPUT),
    TIME_LATENCY_PERCENTILE_40("40.0", true, MetricNameDto.Origin.LATENCY_PERCENTILE),
    TIME_LATENCY_PERCENTILE_50("50.0", true, MetricNameDto.Origin.LATENCY_PERCENTILE),
    TIME_LATENCY_PERCENTILE_60("60.0", true, MetricNameDto.Origin.LATENCY_PERCENTILE),
    TIME_LATENCY_PERCENTILE_70("70.0", true, MetricNameDto.Origin.LATENCY_PERCENTILE),
    TIME_LATENCY_PERCENTILE_80("80.0", true, MetricNameDto.Origin.LATENCY_PERCENTILE),
    TIME_LATENCY_PERCENTILE_90("90.0", true, MetricNameDto.Origin.LATENCY_PERCENTILE),
    TIME_LATENCY_PERCENTILE_95("95.0", true, MetricNameDto.Origin.LATENCY_PERCENTILE),
    TIME_LATENCY_PERCENTILE_99("99.0", true, MetricNameDto.Origin.LATENCY_PERCENTILE);

    private final String description;
    private final boolean isCumulativeCounter;
    private final MetricNameDto.Origin origin;

    private DefaultWorkloadParameters(String description, boolean cumulativeCounter, MetricNameDto.Origin origin) {
        this.description = description;
        isCumulativeCounter = cumulativeCounter;
        this.origin = origin;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCumulativeCounter() {
        return isCumulativeCounter;
    }

    public MetricNameDto.Origin getOrigin() {
        return origin;
    }

    public static DefaultWorkloadParameters fromDescription(String description) {
        for (DefaultWorkloadParameters defaultWorkloadParameter : values()) {
            if (defaultWorkloadParameter.getDescription().equalsIgnoreCase(description)) {
                return defaultWorkloadParameter;
            }
        }
        return null;
    }
}

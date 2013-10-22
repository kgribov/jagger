package com.griddynamics.jaggertest.metric.aggregator;

import com.griddynamics.jagger.engine.e1.collector.MetricAggregator;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: kgribov
 * Date: 10/21/13
 * Time: 7:04 PM
 * To change this template use File | Settings | File Templates.
 */
public class MaxAggregator implements MetricAggregator<Double>{

    private ArrayList<Double> values = new ArrayList<Double>(1000);

    @Override
    public void append(Double calculated) {
        values.add(calculated);
    }

    @Override
    public Double getAggregated() {
        Double max = Double.MIN_VALUE;
        for (Double value : values){
            max = Math.max(max, value);
        }
        return max;
    }

    @Override
    public void reset() {
        values.clear();
    }

    @Override
    public String getName() {
        return "Max";
    }
}

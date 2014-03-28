package metric.aggregator;

import com.griddynamics.jagger.engine.e1.collector.MetricAggregator;
import com.griddynamics.jagger.engine.e1.collector.MetricAggregatorProvider;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: kgribov
 * Date: 10/21/13
 * Time: 7:04 PM
 * To change this template use File | Settings | File Templates.
 */
public class MinMetricAggregatorProvider implements MetricAggregatorProvider{

    @Override
    public MetricAggregator provide() {
        return new MinMetricAggregator();
    }

    private static class MinMetricAggregator implements MetricAggregator<Number> {

        private Double value = null;

        @Override
        public void append(Number calculated) {
            if (value == null) {
                value = calculated.doubleValue();
            }
            else {
                value = Math.min(value,calculated.doubleValue());
            }
        }

        @Override
        public Double getAggregated() {
            return value;
        }

        @Override
        public void reset() {
            value = null;
        }

        @Override
        public String getName() {
            return "min";
        }
    }
}

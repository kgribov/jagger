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
public class MinAggregator implements MetricAggregatorProvider{

    @Override
    public MetricAggregator provide() {
        return new MetricAggregator<Number>() {

            private ArrayList<Number> values = new ArrayList<Number>(1000);

            @Override
            public void append(Number calculated) {
                values.add(calculated);
            }

            @Override
            public Double getAggregated() {
                if (values.isEmpty())
                    return null;

                Double max = Double.MAX_VALUE;
                for (Number value : values){
                    max = Math.min(max, value.doubleValue());
                }
                return max;
            }

            @Override
            public void reset() {
                values.clear();
            }

            @Override
            public String getName() {
                return "min";
            }
        };
    }
}

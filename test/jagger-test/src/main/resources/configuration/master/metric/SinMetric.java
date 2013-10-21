package configuration.master.metric;

import com.griddynamics.jagger.engine.e1.collector.MetricCalculator;

/**
 * Created with IntelliJ IDEA.
 * User: kgribov
 * Date: 10/21/13
 * Time: 6:36 PM
 * To change this template use File | Settings | File Templates.
 */
public class SinMetric implements MetricCalculator{

    private double step = 0;

    @Override
    public Double calculate(Object response) {
        double result = Math.sin(step);
        step += Math.PI/20;
        return result;
    }
}
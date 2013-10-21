package configuration.master.metric;

import com.griddynamics.jagger.engine.e1.collector.MetricCalculator;

/**
 * Created with IntelliJ IDEA.
 * User: kgribov
 * Date: 9/26/13
 * Time: 12:33 PM
 * To change this template use File | Settings | File Templates.
 */
public class AlwaysIncrease implements MetricCalculator {

    private int count = 0;

    @Override
    public Integer calculate(Object response) {
        return new Integer(count++);
    }
}

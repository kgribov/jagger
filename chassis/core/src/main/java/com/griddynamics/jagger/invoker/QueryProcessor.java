package com.griddynamics.jagger.invoker;

/**
 * Created by kgribov on 2/18/14.
 */
public interface QueryProcessor {

    StepInfo preProcess(StepInfo stepInfo);

    StepInfo postProcess(StepResult currentResult);
}

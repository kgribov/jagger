package com.griddynamics.jagger.webclient.server.fetch.implementation;

import com.google.common.collect.Multimap;
import com.griddynamics.jagger.webclient.server.fetch.FetchUtil;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by kgribov on 3/17/14.
 */
public class CustomTestGroupMetricSummaryFetcher extends CustomMetricSummaryFetcher {

    private FetchUtil fetchUtil;

    public void setFetchUtil(FetchUtil fetchUtil) {
        this.fetchUtil = fetchUtil;
    }

    @Override
    protected List<Object[]> getCustomMetricsDataOldModel(Set<Long> taskIds, Set<String> metricIds) {
        return Collections.EMPTY_LIST;
    }

    @Override
    protected List<Object[]> getCustomMetricsDataNewModel(Set<Long> taskIds, Set<String> metricId) {
        Multimap<Long, Long> testGroupMap = fetchUtil.getTestsInTestGroup(taskIds);

        List<Object[]> testGroupsSummary = super.getCustomMetricsDataNewModel(testGroupMap.keySet(), metricId);

        List<Object[]> testsSummary = new ArrayList<Object[]>();

        for (Object[] testGroupSummary : testGroupsSummary){
            Long testGroupId = (Long) testGroupSummary[3];

            for (Long testId : testGroupMap.get(testGroupId)){
                testGroupSummary[3] = new BigInteger(testId.toString());
                testsSummary.add(testGroupSummary);
            }
        }

        return testsSummary;
    }
}

package com.griddynamics.jagger.webclient.server.fetch.implementation;

import com.google.common.collect.Multimap;
import com.griddynamics.jagger.webclient.server.fetch.FetchUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Created by kgribov on 3/17/14.
 */
public class CustomTestGroupMetricPlotFetcher extends CustomMetricPlotFetcher {
    private FetchUtil fetchUtil;

    public void setFetchUtil(FetchUtil fetchUtil) {
        this.fetchUtil = fetchUtil;
    }

    @Override
    protected List<Object[]> getAllRawData(Set<Long> taskIds, Set<String> metricIds) {
        List<Object[]> resultList = new ArrayList<Object[]>();

        Multimap<Long, Long> testGroupMap = fetchUtil.getTestsInTestGroup(taskIds);

        Collection<? extends Object[]> testGroupValues = getPlotDataNewModel(testGroupMap.keySet(), metricIds);

        for (Object[] row : testGroupValues){
            Long testGroupId = (Long) row[0];
            for (Long testId : testGroupMap.get(testGroupId)){
                resultList.add(new Object[]{testId, row[1], row[2], row[3], row[4]});
            }
        }

        return resultList;
    }
}

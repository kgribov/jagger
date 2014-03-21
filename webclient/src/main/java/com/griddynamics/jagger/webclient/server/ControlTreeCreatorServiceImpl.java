package com.griddynamics.jagger.webclient.server;

import com.griddynamics.jagger.agent.model.DefaultMonitoringParameters;
import com.griddynamics.jagger.monitoring.reporting.GroupKey;
import com.griddynamics.jagger.util.MonitoringIdUtils;
import com.griddynamics.jagger.webclient.client.ControlTreeCreatorService;
import com.griddynamics.jagger.webclient.client.components.control.model.*;
import com.griddynamics.jagger.webclient.client.data.MetricRankingProvider;
import com.griddynamics.jagger.webclient.client.dto.MetricNameDto;
import com.griddynamics.jagger.webclient.client.dto.TaskDataDto;
import com.griddynamics.jagger.webclient.server.rules.TreeViewGroupMetricsToNodeRule;
import com.griddynamics.jagger.webclient.server.rules.TreeViewGroupMetricsToNodeRuleProvider;
import com.griddynamics.jagger.webclient.server.rules.TreeViewGroupRule;
import com.griddynamics.jagger.webclient.server.rules.TreeViewGroupRuleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.griddynamics.jagger.webclient.client.mvp.NameTokens.*;

/**
 * Created with IntelliJ IDEA.
 * User: amikryukov
 * Date: 11/26/13
 */
public class ControlTreeCreatorServiceImpl implements ControlTreeCreatorService {

    Logger log = LoggerFactory.getLogger(ControlTreeCreatorServiceImpl.class);

    CommonDataProvider databaseFetcher;

    // todo: implement same idea for fetching plots/summary data
    // to implement parallel fetching data for control tree
    private ExecutorService threadPool;
    private TreeViewGroupRuleProvider treeViewGroupRuleProvider;
    private TreeViewGroupMetricsToNodeRuleProvider treeViewGroupMetricsToNodeRuleProvider;
    private Map<String,Set<String>> defaultMonitoringParams = new HashMap<String, Set<String>>();

    @Required
    public void setTreeViewGroupRuleProvider(TreeViewGroupRuleProvider treeViewGroupRuleProvider) {
        this.treeViewGroupRuleProvider = treeViewGroupRuleProvider;
    }

    @Required
    public void setTreeViewGroupMetricsToNodeRuleProvider(TreeViewGroupMetricsToNodeRuleProvider treeViewGroupMetricsToNodeRuleProvider) {
        this.treeViewGroupMetricsToNodeRuleProvider = treeViewGroupMetricsToNodeRuleProvider;
    }

    @Required
    public void setDefaultMonitoringParams(Map<GroupKey, DefaultMonitoringParameters[]> monitoringPlotGroups) {
        this.defaultMonitoringParams = CommonDataProviderImpl.getDefaultMonitoringParametersMap(monitoringPlotGroups);
    }

    public void setDatabaseFetcher(CommonDataProvider databaseFetcher) {
        this.databaseFetcher = databaseFetcher;
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    @Override
    public RootNode getControlTreeForSession(String sessionId) throws RuntimeException {

        Set<String> dummySet = new HashSet<String>();
        dummySet.add(sessionId);
        return getControlTreeForSessions(dummySet);
    }

    @Override
    public RootNode getControlTreeForSessions(Set<String> sessionIds) throws RuntimeException {

        try {

            long temp = System.currentTimeMillis();

            RootNode rootNode = new RootNode();

            List<TaskDataDto> taskList = fetchTaskDatas(sessionIds);

            Future<SummaryNode> summaryFuture = threadPool.submit(new SummaryNodeFetcherTread(sessionIds, taskList));
            Future<DetailsNode> detailsNodeFuture = threadPool.submit(new DetailsNodeFetcherTread(sessionIds, taskList));
            //Future<SessionScopePlotsNode> sessionScopePlotsNodeFuture = threadPool.submit(new SessionScopePlotsNodeFetcherThread(sessionIds));

            SummaryNode summaryNode = summaryFuture.get();
            DetailsNode detailsNode = detailsNodeFuture.get();
            //SessionScopePlotsNode sessionScopePlotsNode = sessionScopePlotsNodeFuture.get();

            // todo restore session scope plots for test group metrics JFG_667
            // temporary disabled session scope plots while transferring monitoring to metrics
            //detailsNode.setSessionScopePlotsNode(sessionScopePlotsNode);

            rootNode.setSummary(summaryNode);
            rootNode.setDetailsNode(detailsNode);

            log.info("Total time fetching all data for control tree : {} ms", (System.currentTimeMillis() - temp));

            return rootNode;
        } catch (Throwable th) {
            log.error("Error while creating Control Tree", th);
            th.printStackTrace();
            throw new RuntimeException(th);
        }
    }

    private List<TaskDataDto> fetchTaskDatas(Set<String> sessionIds) {
        long temp = System.currentTimeMillis();
        List<TaskDataDto> tddos = databaseFetcher.getTaskDataForSessions(sessionIds);
        log.debug("load tests : {} for summary with {} ms", tddos, System.currentTimeMillis() - temp);
        return tddos;
    }

    private List<TestDetailsNode> getDetailsTaskNodeList(final Set<String> sessionIds, final List<TaskDataDto> taskList) {
        List<TestDetailsNode> taskDataDtoList = new ArrayList<TestDetailsNode>();

        try {
            Future<Map<TaskDataDto, List<PlotNode>>> metricsPlotsMapFuture = threadPool.submit(
                new Callable<Map<TaskDataDto, List<PlotNode>>>() {
                    @Override
                    public Map<TaskDataDto, List<PlotNode>> call() throws Exception {
                        return getTestPlotsMap(sessionIds, taskList);
                    }
                }
            );

            Future<Map<TaskDataDto, List<PlotNode>>> monitoringNewPlotsMapFuture = threadPool.submit(
                    new Callable<Map<TaskDataDto, List<PlotNode>>>() {
                        @Override
                        public Map<TaskDataDto, List<PlotNode>> call() throws Exception {
                            return getMonitoringPlots(sessionIds, taskList);
                        }
                    }
            );

            Map<TaskDataDto, List<PlotNode>> map = metricsPlotsMapFuture.get();
            Map<TaskDataDto, List<PlotNode>> monitoringMap = monitoringNewPlotsMapFuture.get();

            // get agent names
            Set<PlotNode> plotNodeList = new HashSet<PlotNode>();
            if (!monitoringMap.isEmpty()) {
                for (TaskDataDto taskDataDto : monitoringMap.keySet()) { plotNodeList.addAll(monitoringMap.get(taskDataDto));}}
            for (TaskDataDto taskDataDto : map.keySet()) { plotNodeList.addAll(map.get(taskDataDto));}
            Map<String,Set<String>> agentNames = getAgentNamesForMonitoringParameters(plotNodeList);

            // get tree
            for (TaskDataDto tdd : taskList) {
                List<PlotNode> metricNodeList = map.get(tdd);
                if (!monitoringMap.isEmpty()) {
                    metricNodeList.addAll(monitoringMap.get(tdd));
                }

                String rootId = METRICS_PREFIX + tdd.hashCode();

                if (metricNodeList.size() > 0) {
                    // apply rules how to build tree
                    MetricGroupNode<PlotNode> testDetailsNodeBase = buildTreeAccordingToRules(rootId, agentNames, metricNodeList);

                    // full test details node
                    TestDetailsNode testNode = new TestDetailsNode(testDetailsNodeBase);
                    testNode.setTaskDataDto(tdd);

                    taskDataDtoList.add(testNode);
                }
            }

            MetricRankingProvider.sortPlotNodes(taskDataDtoList);
            return taskDataDtoList;

        } catch (Exception e) {
            log.error("Exception occurs while fetching plotNames for sessions {}, and tests {}", sessionIds, taskList);
            throw new RuntimeException(e);
        }
    }

    private Map<TaskDataDto, List<PlotNode>> getTestPlotsMap(Set<String> sessionIds, List<TaskDataDto> taskList) {
        return databaseFetcher.getTestPlotsMap(sessionIds, taskList);
    }

    private List<MonitoringSessionScopePlotNode> getSessionScopePlotNames(Set<String> sessionIds) {
        return databaseFetcher.getSessionScopeMonitoringPlotNodes(sessionIds);
    }

    private List<TestNode> getSummaryTaskNodeList(List<TaskDataDto> tasks) {

        List<TestNode> taskDataDtoList = new ArrayList<TestNode>();

        Map<TaskDataDto, List<MetricNode>> map = getTestMetricsMap(tasks);
        for (TaskDataDto tdd : map.keySet()) {
            List<MetricNode> metricNodeList = map.get(tdd);
            String rootId = SUMMARY_PREFIX + tdd.hashCode();

            if (metricNodeList.size() > 0) {
                // apply rules how to build tree
                MetricGroupNode<MetricNode> testNodeBase = buildTreeAccordingToRules(rootId, null, metricNodeList);

                // full test node with info data
                TestNode testNode = new TestNode(testNodeBase);
                testNode.setTaskDataDto(tdd);
                TestInfoNode tin = new TestInfoNode(TEST_INFO + testNode.getId(), TEST_INFO);
                testNode.setTestInfo(tin);

                taskDataDtoList.add(testNode);
            }
        }

        MetricRankingProvider.sortPlotNodes(taskDataDtoList);
        return taskDataDtoList;
    }

    private Map<TaskDataDto, List<MetricNode>> getTestMetricsMap(List<TaskDataDto> tddos) {
        return databaseFetcher.getTestMetricsMap(tddos, threadPool);
    }

    private Map<TaskDataDto, List<PlotNode>> getMonitoringPlots(Set<String> sessionIds, List<TaskDataDto> tdds) {
        return databaseFetcher.getMonitoringPlotNodes(sessionIds, tdds);
    }

    public class SummaryNodeFetcherTread
            implements Callable<SummaryNode> {
        private Set<String> sessionIds;
        private List<TaskDataDto> taskList;
        public SummaryNodeFetcherTread(Set<String> sessionIds, List<TaskDataDto> taskList) {
            this.sessionIds = sessionIds;
            this.taskList = taskList;
        }

        public SummaryNode call() {
            SummaryNode sn = new SummaryNode(CONTROL_SUMMARY_TRENDS, CONTROL_SUMMARY_TRENDS);
            SessionInfoNode sin = new SessionInfoNode(SESSION_INFO, SESSION_INFO);
            sn.setSessionInfo(sin);
            if (!taskList.isEmpty()) {
                sn.setTests(getSummaryTaskNodeList(taskList));
            }
            return sn;
        }
    }

    public class DetailsNodeFetcherTread
            implements Callable<DetailsNode> {
        private Set<String> sessionIds;
        private List<TaskDataDto> taskList;
        public DetailsNodeFetcherTread(Set<String> sessionIds, List<TaskDataDto> taskList) {
            this.sessionIds = sessionIds;
            this.taskList = taskList;
        }

        public DetailsNode call() {
            DetailsNode dn = new DetailsNode(CONTROL_METRICS, CONTROL_METRICS);
            if (!taskList.isEmpty()) {
                dn.setTests(getDetailsTaskNodeList(sessionIds, taskList));
            }
            return dn;
        }
    }

    public class SessionScopePlotsNodeFetcherThread
            implements Callable<SessionScopePlotsNode> {

        private Set<String> sessionIds;

        public SessionScopePlotsNodeFetcherThread(Set<String> sessionIds) {
            this.sessionIds = sessionIds;
        }

        @Override
        public SessionScopePlotsNode call() throws Exception {
            if (sessionIds.size() == 1) {
                SessionScopePlotsNode sspn = new SessionScopePlotsNode(SESSION_SCOPE_PLOTS, SESSION_SCOPE_PLOTS);
                sspn.setPlots(getSessionScopePlotNames(sessionIds));
                if (!sspn.getPlots().isEmpty()) {
                    return sspn;
                }
            }
            return null;
        }
    }

    private Map<String,Set<String>> getAgentNamesForMonitoringParameters(Set<PlotNode> plotNodeList) {
        Map<String,Set<String>> agentNames = new HashMap<String, Set<String>>();

        for (PlotNode plotNode : plotNodeList) {
            for (MetricNameDto metricNameDto : plotNode.getMetricNameDtoList()) {
                // old monitoring or new monitoring as metrics
                if ((metricNameDto.getOrigin() == MetricNameDto.Origin.MONITORING) ||
                        (metricNameDto.getOrigin() == MetricNameDto.Origin.TEST_GROUP_METRIC)) {

                    // if looks like monitoring parameter
                    MonitoringIdUtils.MonitoringId monitoringId = MonitoringIdUtils.splitMonitoringMetricId(metricNameDto.getMetricName());
                    if (monitoringId != null) {
                        // if available in default monitoring parameters
                        for (String key : defaultMonitoringParams.keySet()) {
                            if (defaultMonitoringParams.get(key).contains(monitoringId.getMonitoringName())) {
                                if (!agentNames.containsKey(key)) {
                                    agentNames.put(key,new HashSet<String>());
                                }
                                agentNames.get(key).add(monitoringId.getAgentName());
                            }
                        }
                    }
                }
            }
        }

        return agentNames;
    }

    private <M extends MetricNode> MetricGroupNode<M> buildTreeAccordingToRules(String rootId, Map<String, Set<String>> agentNames, List<M> metricNodeList)
    {
        // rules to unite metrics in single plot
        TreeViewGroupMetricsToNodeRule unitedMetricsRule = treeViewGroupMetricsToNodeRuleProvider.provide(agentNames);
        // unite metrics and add result to original list
        List<M> unitedMetrics = unitedMetricsRule.filter(rootId, metricNodeList);
        if (unitedMetrics != null) {
            metricNodeList.addAll(unitedMetrics);
        }

        // rules to create test tree view
        TreeViewGroupRule groupedNodesRule = treeViewGroupRuleProvider.provide(rootId, rootId);
        // tree with metrics distributed by groups
        MetricGroupNode<M> testNodeBase = groupedNodesRule.filter(null,metricNodeList);

        return testNodeBase;
    }

}

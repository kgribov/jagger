package com.griddynamics.jagger.webclient.server;

import com.google.common.collect.Multimap;
import com.griddynamics.jagger.agent.model.DefaultMonitoringParameters;
import com.griddynamics.jagger.engine.e1.aggregator.workload.model.WorkloadProcessLatencyPercentile;
import com.griddynamics.jagger.monitoring.reporting.GroupKey;
import com.griddynamics.jagger.util.MonitoringIdUtils;
import com.griddynamics.jagger.webclient.client.components.control.model.MetricNode;
import com.griddynamics.jagger.webclient.client.components.control.model.MonitoringSessionScopePlotNode;
import com.griddynamics.jagger.webclient.client.components.control.model.PlotNode;
import com.griddynamics.jagger.webclient.client.components.control.model.SessionPlotNode;
import com.griddynamics.jagger.webclient.client.data.MetricRankingProvider;
import com.griddynamics.jagger.webclient.client.data.WebClientProperties;
import com.griddynamics.jagger.webclient.client.dto.MetricNameDto;
import com.griddynamics.jagger.webclient.client.dto.SessionPlotNameDto;
import com.griddynamics.jagger.webclient.client.dto.TaskDataDto;
import com.griddynamics.jagger.webclient.server.fetch.FetchUtil;
import com.griddynamics.jagger.webclient.server.fetch.MetricNameUtil;
import com.griddynamics.jagger.webclient.server.plot.CustomMetricPlotNameProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.griddynamics.jagger.webclient.client.mvp.NameTokens.*;

/**
 * Created with IntelliJ IDEA.
 * User: amikryukov
 * Date: 11/27/13
 */
public class CommonDataProviderImpl implements CommonDataProvider {

    private EntityManager entityManager;

    private Logger log = LoggerFactory.getLogger(this.getClass());

    private WebClientProperties webClientProperties;
    private FetchUtil fetchUtil;

    @PersistenceContext
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    private CustomMetricPlotNameProvider customMetricPlotNameProvider;
    private Map<GroupKey, DefaultWorkloadParameters[]> workloadPlotGroups;
    private Map<GroupKey, DefaultMonitoringParameters[]> monitoringPlotGroups;

    public Map<GroupKey, DefaultMonitoringParameters[]> getMonitoringPlotGroups() {
        return monitoringPlotGroups;
    }

    @Override
    public Map<String,Set<String>> getDefaultMonitoringParameters() {
        return getDefaultMonitoringParametersMap(monitoringPlotGroups);
    }

    public static Map<String,Set<String>> getDefaultMonitoringParametersMap(Map<GroupKey, DefaultMonitoringParameters[]> monitoringPlotGroups) {
        // relation of old monitoring names from Groupkey (were used in hyperlinks) to
        // new monitoring metric ids from DefaultMonitoringParameters
        // necessary to process old hyperlinks by new client
        Map<String,Set<String>> result = new HashMap<String, Set<String>>();

        for(Map.Entry<GroupKey,DefaultMonitoringParameters[]> groupKeyEntry : monitoringPlotGroups.entrySet()) {
            String key = groupKeyEntry.getKey().getUpperName();
            if (!result.containsKey(key)) {
                result.put(key,new HashSet<String>());
            }

            for (DefaultMonitoringParameters defaultMonitoringParameters : groupKeyEntry.getValue()) {
                result.get(key).add(defaultMonitoringParameters.getId());
            }
        }

        return result;
    }

    @Required
    public void setMonitoringPlotGroups(Map<GroupKey, DefaultMonitoringParameters[]> monitoringPlotGroups) {
        this.monitoringPlotGroups = monitoringPlotGroups;
    }

    public Map<GroupKey, DefaultWorkloadParameters[]> getWorkloadPlotGroups() {
        return workloadPlotGroups;
    }

    @Required
    public void setWorkloadPlotGroups(Map<GroupKey, DefaultWorkloadParameters[]> workloadPlotGroups) {
        this.workloadPlotGroups = workloadPlotGroups;
    }

    public CustomMetricPlotNameProvider getCustomMetricPlotNameProvider() {
        return customMetricPlotNameProvider;
    }

    public void setCustomMetricPlotNameProvider(CustomMetricPlotNameProvider customMetricPlotNameProvider) {
        this.customMetricPlotNameProvider = customMetricPlotNameProvider;
    }

    private List<MetricNameDto> standardMetricNameDtoList;

    @Required
    public void setStandardMetricNameDtoList(List<MetricNameDto> standardMetricNameDtoList) {
        this.standardMetricNameDtoList = standardMetricNameDtoList;
    }


    /**
     * Fetch custom metrics names from database
     * @param tests tests data
     * @return set of MetricNameDto representing name of metric
     */
    public Set<MetricNameDto> getCustomMetricsNames(List<TaskDataDto> tests){

        Set<MetricNameDto>  metrics = new HashSet<MetricNameDto>();

        long temp = System.currentTimeMillis();

        metrics.addAll(getCustomMetricsNamesNewModel(tests));

        metrics.addAll(getCustomTestGroupMetricsNamesNewModel(tests));

        metrics.addAll(getCustomMetricsNamesOldModel(tests));

        log.debug("{} ms spent for fetching {} custom metrics", System.currentTimeMillis() - temp, metrics.size());

        return metrics;
    }


    public Set<MetricNameDto> getCustomMetricsNamesOldModel(List<TaskDataDto> tests) {
        Set<Long> taskIds = new HashSet<Long>();
        Set<String> sessionIds = new HashSet<String>();
        for (TaskDataDto tdd : tests) {
            taskIds.addAll(tdd.getIds());
            sessionIds.addAll(tdd.getSessionIds());
        }

        long temp = System.currentTimeMillis();

        // check old data (before jagger 1.2.4 version)
        List<Object[]> metricNames = entityManager.createNativeQuery(
                "select dre.name, selected.id from DiagnosticResultEntity dre join " +
                " (" +
                "  select wd.workloaddataID, td.id from " +
                "    (" +
                "     select wd.id as workloaddataID, wd.sessionId, wd.taskId from WorkloadData wd where wd.sessionId in (:sessionIds)" +
                "    ) as wd join   " +
                "    ( " +
                "     select td.taskId, td.sessionId, td.id from TaskData td where td.id in (:taskIds)" +
                "    ) as td on wd.taskId=td.taskId and wd.sessionId=td.sessionId" +
                " ) as selected on dre.workloadData_id=selected.workloaddataID")
                .setParameter("taskIds", taskIds)
                .setParameter("sessionIds", sessionIds)
                .getResultList();

        if (metricNames.isEmpty()) {
            return Collections.EMPTY_SET;
        }

        Set<MetricNameDto> metrics = new HashSet<MetricNameDto>(metricNames.size());

        log.debug("{} ms spent for fetching {} custom metrics", System.currentTimeMillis() - temp, metricNames.size());

        for (Object[] name : metricNames){
            if (name == null || name[0] == null) continue;
            for (TaskDataDto td : tests) {
                if (td.getIds().contains(((BigInteger)name[1]).longValue())) {
                    MetricNameDto metric = new MetricNameDto();
                    metric.setTest(td);
                    metric.setMetricName((String) name[0]);
                    metric.setOrigin(MetricNameDto.Origin.METRIC);
                    metrics.add(metric);
                    break;
                }
            }
        }

        return metrics;

    }

    public Set<MetricNameDto> getCustomMetricsNamesNewModel(List<TaskDataDto> tests) {
        try {
            Set<Long> taskIds = CommonUtils.getTestsIds(tests);

            List<Object[]> metricDescriptionEntities = getMetricNames(taskIds);

            if (metricDescriptionEntities.isEmpty()) {
                return Collections.EMPTY_SET;
            }

            Set<MetricNameDto>  metrics = new HashSet<MetricNameDto>(metricDescriptionEntities.size());

            for (Object[] mde : metricDescriptionEntities) {
                for (TaskDataDto td : tests) {
                    if (td.getIds().contains((Long) mde[2])) {
                        metrics.add(new MetricNameDto(td, (String) mde[0], (String) mde[1], MetricNameDto.Origin.METRIC));
                        break;
                    }
                }
            }

            return metrics;
        } catch (PersistenceException e) {
            log.debug("Could not fetch data from MetricSummaryEntity: {}", DataProcessingUtil.getMessageFromLastCause(e));
            return Collections.EMPTY_SET;
        }
    }

    public Set<MetricNameDto> getCustomTestGroupMetricsNamesNewModel(List<TaskDataDto> tests) {
        try {
            Set<Long> taskIds = CommonUtils.getTestsIds(tests);

            Multimap<Long, Long> testGroupMap = fetchUtil.getTestsInTestGroup(taskIds);

            List<Object[]> metricDescriptionEntities = getMetricNames(testGroupMap.keySet());

            metricDescriptionEntities = CommonUtils.filterMonitoring(metricDescriptionEntities, monitoringPlotGroups);

            if (metricDescriptionEntities.isEmpty()) {
                return Collections.EMPTY_SET;
            }

            Set<MetricNameDto>  metrics = new HashSet<MetricNameDto>(metricDescriptionEntities.size());

            // add test-group metric names
            for (Object[] mde : metricDescriptionEntities){
                for (TaskDataDto td : tests){
                    Collection<Long> allTestsInGroup = testGroupMap.get((Long)mde[2]);
                    if (CommonUtils.containsAtLeastOne(td.getIds(), allTestsInGroup)){
                        metrics.add(new MetricNameDto(td, (String)mde[0], (String)mde[1], MetricNameDto.Origin.TEST_GROUP_METRIC));
                    }
                }
            }

            return metrics;
        } catch (PersistenceException e) {
            log.debug("Could not fetch test-group data from MetricSummaryEntity: {}", DataProcessingUtil.getMessageFromLastCause(e));
            return Collections.EMPTY_SET;
        }
    }

    private List<Object[]> getMetricNames(Set<Long> taskIds){
        if (taskIds.isEmpty()){
            return Collections.EMPTY_LIST;
        }
        return entityManager.createQuery(
                "select mse.metricDescription.metricId, mse.metricDescription.displayName, mse.metricDescription.taskData.id " +
                        "from MetricSummaryEntity as mse where mse.metricDescription.taskData.id in (:taskIds)")
                .setParameter("taskIds", taskIds)
                .getResultList();
    }

    /**
     * Fetch validators names from database
     * @param tests tests data
     * @return set of MetricNameDto representing name of validator
     */
    public Set<MetricNameDto> getValidatorsNames(List<TaskDataDto> tests){

        Set<Long> taskIds = new HashSet<Long>();
        Set<String> sessionIds = new HashSet<String>();
        for (TaskDataDto tdd : tests) {
            taskIds.addAll(tdd.getIds());
            sessionIds.addAll(tdd.getSessionIds());
        }

        long temp = System.currentTimeMillis();

        Set<MetricNameDto> validators = getValidatorsNamesNewModel(tests);
        if (validators == null) { // some exception occured

            List<Object[]> validatorNames = entityManager.createNativeQuery(
                    "select v.validator, selected.taskdataID from ValidationResultEntity v join " +
                            "(" +
                            "  select wd.workloaddataID, td.taskdataID from " +
                            "    ( " +
                            "      select wd.id as workloaddataID, wd.taskId, wd.sessionId from WorkloadData wd where wd.sessionId in (:sessionIds)" +
                            "    ) as wd join   " +
                            "    ( " +
                            "      select td.id as taskdataID, td.taskId, td.sessionId from TaskData td where td.id in (:taskIds)" +
                            "    ) as td on wd.taskId=td.taskId and wd.sessionId=td.sessionId" +
                            ") as selected on v.workloadData_id=selected.workloaddataID")
                    .setParameter("taskIds", taskIds)
                    .setParameter("sessionIds", sessionIds)
                    .getResultList();
            log.debug("{} ms spent for fetching {} validators", System.currentTimeMillis() - temp, validatorNames.size());

            validators = new HashSet<MetricNameDto>(validatorNames.size());

            for (Object[] name : validatorNames){
                if (name == null || name[0] == null) continue;
                for (TaskDataDto td : tests) {
                    if (td.getIds().contains(((BigInteger)name[1]).longValue())) {
                        MetricNameDto metric = new MetricNameDto();
                        metric.setTest(td);
                        metric.setMetricName((String) name[0]);
                        metric.setOrigin(MetricNameDto.Origin.VALIDATOR);
                        validators.add(metric);
                        break;
                    }
                }
            }
        }

        return validators;
    }


    public Set<MetricNameDto> getValidatorsNamesNewModel(List<TaskDataDto> tests) {
        try {
            Set<Long> taskIds = new HashSet<Long>();
            Set<String> sessionIds = new HashSet<String>();
            for (TaskDataDto tdd : tests) {
                taskIds.addAll(tdd.getIds());
                sessionIds.addAll(tdd.getSessionIds());
            }

            long temp = System.currentTimeMillis();

            List<Object[]> validatorNames = entityManager.createNativeQuery(
                    "select v.validator, selected.taskdataID, v.displayName from ValidationResultEntity v join " +
                            "(" +
                            "  select wd.workloaddataID, td.taskdataID from " +
                            "    ( " +
                            "      select wd.id as workloaddataID, wd.taskId, wd.sessionId from WorkloadData wd where wd.sessionId in (:sessionIds)" +
                            "    ) as wd join   " +
                            "    ( " +
                            "      select td.id as taskdataID, td.taskId, td.sessionId from TaskData td where td.id in (:taskIds)" +
                            "    ) as td on wd.taskId=td.taskId and wd.sessionId=td.sessionId" +
                            ") as selected on v.workloadData_id=selected.workloaddataID")
                    .setParameter("taskIds", taskIds)
                    .setParameter("sessionIds", sessionIds)
                    .getResultList();
            log.debug("{} ms spent for fetching {} validators", System.currentTimeMillis() - temp, validatorNames.size());

            if (validatorNames.isEmpty()) {
                return Collections.EMPTY_SET;
            }
            Set<MetricNameDto> validators = new HashSet<MetricNameDto>(validatorNames.size());

            for (Object[] name : validatorNames){
                if (name == null || name[0] == null) continue;
                for (TaskDataDto td : tests) {
                    if (td.getIds().contains(((BigInteger)name[1]).longValue())) {
                        MetricNameDto metric = new MetricNameDto();
                        metric.setTest(td);
                        metric.setMetricName((String) name[0]);
                        metric.setMetricDisplayName((String) name[2]);
                        metric.setOrigin(MetricNameDto.Origin.VALIDATOR);
                        validators.add(metric);
                        break;
                    }
                }
            }

            return validators;
        } catch (PersistenceException e) {
            log.debug("Could not fetch validators names from new model of ValidationResultEntity: {}", DataProcessingUtil.getMessageFromLastCause(e));
            return null;
        }
    }


    public Set<MetricNameDto> getLatencyMetricsNames(List<TaskDataDto> tests){
        Set<MetricNameDto> latencyNames;

        Set<Long> testIds = new HashSet<Long>();
        for (TaskDataDto tdd : tests) {
            testIds.addAll(tdd.getIds());
        }

        long temp = System.currentTimeMillis();
        List<WorkloadProcessLatencyPercentile> latency = entityManager.createQuery(
                "select s from  WorkloadProcessLatencyPercentile as s where s.workloadProcessDescriptiveStatistics.taskData.id in (:taskIds) ")
                .setParameter("taskIds", testIds)
                .getResultList();


        log.debug("{} ms spent for Latency Percentile fetching (size ={})", System.currentTimeMillis() - temp, latency.size());

        latencyNames = new HashSet<MetricNameDto>(latency.size());

        if (!latency.isEmpty()){


            for(WorkloadProcessLatencyPercentile percentile : latency) {
                for (TaskDataDto tdd : tests) {

                    if (tdd.getIds().contains(percentile.getWorkloadProcessDescriptiveStatistics().getTaskData().getId())) {
                        MetricNameDto dto = new MetricNameDto();
                        dto.setMetricName(MetricNameUtil.getLatencyMetricName(percentile.getPercentileKey()));
                        dto.setMetricDisplayName(MetricNameUtil.getLatencyMetricName(percentile.getPercentileKey()));
                        dto.setTest(tdd);
                        dto.setOrigin(MetricNameDto.Origin.LATENCY_PERCENTILE);
                        latencyNames.add(dto);
                        break;
                    }
                }
            }
        }
        return latencyNames;
    }


    @Override
    public Map<TaskDataDto, List<PlotNode>> getMonitoringPlotNodes(Set<String> sessionIds, List<TaskDataDto> taskDataDtos) {

        try {
            Map<TaskDataDto, List<BigInteger>>  monitoringIds = getMonitoringIds(sessionIds, taskDataDtos);
            if (monitoringIds.isEmpty()) {
                return Collections.EMPTY_MAP;
            }

            Map<TaskDataDto, List<PlotNode>> result = getMonitoringPlotNames(monitoringPlotGroups.entrySet(), monitoringIds);

            if (result.isEmpty()) {
                return Collections.EMPTY_MAP;
            }

            log.debug("For sessions {} are available these plots: {}", sessionIds, result);
            return result;

        } catch (Exception e) {
            log.error("Error was occurred during task scope plots data getting for session IDs " + sessionIds + ", tasks  " + taskDataDtos, e);
            throw new RuntimeException(e);
        }
    }


    /**
     * Fetch all Monitoring tasks ids for all tests
     * @param sessionIds all sessions
     * @param taskDataDtos all tests
     * @return list of monitoring task ids
     */
    private Map<TaskDataDto, List<BigInteger>> getMonitoringIds(Set<String> sessionIds, List<TaskDataDto> taskDataDtos) {

        Set<Long> taskIds = new HashSet<Long>();
        for (TaskDataDto tdd : taskDataDtos) {
            taskIds.addAll(tdd.getIds());
        }


        long temp = System.currentTimeMillis();
        List<Object[]> monitoringTaskIds = entityManager.createNativeQuery(
                "select test.id, some.taskDataId from " +
                        "  ( " +
                        "    select test.id, test.sessionId, test.taskId from TaskData as test where test.sessionId in (:sessionIds)" +
                        "  ) as test join " +
                        "  (" +
                        "    select some.parentId, pm.monitoringId, some.taskDataId, pm.sessionId from" +
                        "      (" +
                        "        select pm.monitoringId, pm.sessionId, pm.parentId from PerformedMonitoring as pm where pm.sessionId in (:sessionIds) " +
                        "      ) as pm join " +
                        "      (" +
                        "        select td2.sessionId, td2.id as taskDataId, wd.parentId from" +
                        "          ( " +
                        "            select wd.parentId, wd.sessionId, wd.taskId from WorkloadData as wd where wd.sessionId in (:sessionIds)" +
                        "          ) as wd join " +
                        "            TaskData as td2" +
                        "            on td2.id in (:taskIds)" +
                        "            and wd.sessionId = td2.sessionId" +
                        "            and wd.taskId=td2.taskId" +
                        "      ) as some on pm.sessionId = some.sessionId and pm.parentId=some.parentId" +
                        "  ) as some on test.sessionId = some.sessionId and test.taskId=some.monitoringId"
        )
                .setParameter("taskIds", taskIds)
                .setParameter("sessionIds", sessionIds)
                .getResultList();
        log.debug("db call to fetch all monitoring tasks ids in {} ms (size : {})", System.currentTimeMillis() - temp, monitoringTaskIds.size());

        Map<TaskDataDto, List<BigInteger>> result = new HashMap<TaskDataDto, List<BigInteger>>();
        if (monitoringTaskIds.isEmpty()) {
            return Collections.EMPTY_MAP;
        }


        for (Object[] ids : monitoringTaskIds) {
            for (TaskDataDto tdd : taskDataDtos) {
                if (tdd.getIds().contains(((BigInteger)ids[1]).longValue())) {
                    if (!result.containsKey(tdd)) {
                        result.put(tdd, new ArrayList<BigInteger>());
                    }
                    result.get(tdd).add(((BigInteger)ids[0]));
                    break;
                }
            }
        }

        return result;
    }


    @Override
    public List<MonitoringSessionScopePlotNode> getSessionScopeMonitoringPlotNodes(Set<String> sessionIds) {

        List<MonitoringSessionScopePlotNode> monitoringPlotNodes;
        try {

            monitoringPlotNodes = getMonitoringPlotNamesNew(sessionIds);
            log.debug("For sessions {} are available these plots: {}", sessionIds, monitoringPlotNodes);
        } catch (Exception e) {
            log.error("Error was occurred during task scope plots data getting for session IDs " + sessionIds, e);
            throw new RuntimeException(e);
        }

        if (monitoringPlotNodes == null) {
            return Collections.EMPTY_LIST;
        }
        return monitoringPlotNodes;
    }


    @Override
    public Map<TaskDataDto, List<MetricNode>> getTestMetricsMap(final List<TaskDataDto> tddos, ExecutorService treadPool) {

        Long time = System.currentTimeMillis();
        List<MetricNameDto> list = new ArrayList<MetricNameDto>();
        for (TaskDataDto taskDataDto : tddos){
            for (MetricNameDto metricNameDto : standardMetricNameDtoList) {
                MetricNameDto metric = new MetricNameDto();
                metric.setMetricName(metricNameDto.getMetricName());
                metric.setMetricDisplayName(metricNameDto.getMetricDisplayName());
                metric.setOrigin(metricNameDto.getOrigin());
                metric.setTest(taskDataDto);
                metric.setMetricNameSynonyms(metricNameDto.getMetricNameSynonyms());
                list.add(metric);
            }
        }

        try {

            Future<Set<MetricNameDto>> latencyMetricNamesFuture = treadPool.submit(
                    new Callable<Set<MetricNameDto>>(){

                        @Override
                        public Set<MetricNameDto> call() throws Exception {
                            return getLatencyMetricsNames(tddos);
                        }
                    }
            );

            Future<Set<MetricNameDto>> customMetricNamesFuture = treadPool.submit(
                    new Callable<Set<MetricNameDto>>(){

                        @Override
                        public Set<MetricNameDto> call() throws Exception {
                            return getCustomMetricsNames(tddos);
                        }
                    }
            );

            Future<Set<MetricNameDto>> validatorsNamesFuture = treadPool.submit(
                    new Callable<Set<MetricNameDto>>(){

                        @Override
                        public Set<MetricNameDto> call() throws Exception {
                            return getValidatorsNames(tddos);
                        }
                    }
            );

            list.addAll(latencyMetricNamesFuture.get());
            list.addAll(customMetricNamesFuture.get());
            list.addAll(validatorsNamesFuture.get());
        } catch (Exception e) {
            log.error("Exception occurs while fetching MetricNames for tests : ", e);
            throw new RuntimeException(e);
        }

        log.info("For tasks {} was found {} metrics names for {} ms", new Object[]{tddos, list.size(), System.currentTimeMillis() - time});

        Map<TaskDataDto, List<MetricNode>> result = new HashMap<TaskDataDto, List<MetricNode>>();

        for (MetricNameDto mnd : list) {
            for (TaskDataDto tdd : tddos) {
                if (tdd.getIds().containsAll(mnd.getTaskIds())) {
                    if (!result.containsKey(tdd)) {
                        result.put(tdd, new ArrayList<MetricNode>());
                    }
                    MetricNode mn = new MetricNode();
                    String id = SUMMARY_PREFIX + tdd.hashCode() + mnd.getMetricName();
                    mn.init(id, mnd.getMetricDisplayName(), Arrays.asList(mnd));
                    result.get(tdd).add(mn);
                    break;
                }
            }
        }

        return result;
    }

    @Override
    public Map<TaskDataDto, List<PlotNode>> getTestPlotsMap(Set<String> sessionIds, List<TaskDataDto> taskList) {

        Map<TaskDataDto, List<PlotNode>> result = new HashMap<TaskDataDto, List<PlotNode>>();

        List<MetricNameDto> metricNameDtoList = new ArrayList<MetricNameDto>();
        try {

            Map<TaskDataDto, Boolean> isWorkloadMap = isWorkloadStatisticsAvailable(taskList);
            for (Map.Entry<TaskDataDto, Boolean> entry: isWorkloadMap.entrySet()) {
                if (entry.getValue()) {
                    for (Map.Entry<GroupKey, DefaultWorkloadParameters[]> monitoringPlot : workloadPlotGroups.entrySet()) {
                        MetricNameDto metricNameDto = new MetricNameDto(entry.getKey(), monitoringPlot.getKey().getUpperName());
                        metricNameDto.setOrigin(monitoringPlot.getValue()[0].getOrigin());
                        metricNameDtoList.add(metricNameDto);
                    }
                }
            }

            Set<MetricNameDto> customMetrics = customMetricPlotNameProvider.getPlotNames(taskList);

            metricNameDtoList.addAll(customMetrics);

            log.debug("For sessions {} are available these plots: {}", sessionIds, metricNameDtoList);

            for (MetricNameDto pnd : metricNameDtoList) {
                for (TaskDataDto tdd : taskList) {
                    if (tdd.getIds().containsAll(pnd.getTaskIds())) {
                        if (!result.containsKey(tdd)) {
                            result.put(tdd, new ArrayList<PlotNode>());
                        }
                        PlotNode pn = new PlotNode();
                        String id = METRICS_PREFIX + tdd.hashCode() + pnd.getMetricName();
                        pn.init(id, pnd.getMetricDisplayName(), Arrays.asList(pnd));
                        result.get(tdd).add(pn);
                        break;
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error was occurred during task scope plots data getting for session IDs " + sessionIds + ", tasks : " + taskList, e);
            throw new RuntimeException(e);
        }

        return result;
    }

    private List<MonitoringSessionScopePlotNode> getMonitoringPlotNamesNew(Set<String> sessionIds) {

        long temp = System.currentTimeMillis();
        List<Object[]> agentIdentifierObjects =
                entityManager.createNativeQuery("select ms.boxIdentifier, ms.systemUnderTestUrl, ms.description from MonitoringStatistics as ms" +
                        "  where ms.sessionId in (:sessionId)" +
                        " group by ms.description, ms.boxIdentifier, ms.systemUnderTestUrl")
                        .setParameter("sessionId", sessionIds)
                        .getResultList();
        log.debug("db call to fetch session scope monitoring in {} ms (size: {})", System.currentTimeMillis() - temp, agentIdentifierObjects.size());

        if (agentIdentifierObjects.size() == 0) {
            return Collections.EMPTY_LIST;
        }

        Map<String, MonitoringSessionScopePlotNode> tempMap = new HashMap<String, MonitoringSessionScopePlotNode>();

        Set<Map.Entry<GroupKey, DefaultMonitoringParameters[]>> set = monitoringPlotGroups.entrySet();
        for (Object[] objects : agentIdentifierObjects) {

            String groupKey = findMonitoringKey((String)objects[2], set);
            if (groupKey == null) {

                continue;
            }

            if (!tempMap.containsKey(groupKey)) {

                MonitoringSessionScopePlotNode monitoringPlotNode = new MonitoringSessionScopePlotNode();
                monitoringPlotNode.setId(MONITORING_PREFIX + groupKey);
                monitoringPlotNode.setDisplayName(groupKey);
                monitoringPlotNode.setPlots(new ArrayList<SessionPlotNode>());
                tempMap.put(groupKey, monitoringPlotNode);
            }

            MonitoringSessionScopePlotNode monitoringPlotNode = tempMap.get(groupKey);

            SessionPlotNode plotNode = new SessionPlotNode();
            String agentIdenty = objects[0] == null ? objects[1].toString() : objects[0].toString();
            plotNode.setPlotNameDto(new SessionPlotNameDto(sessionIds, MonitoringIdUtils.getMonitoringMetricId(groupKey, agentIdenty)));
            plotNode.setDisplayName(agentIdenty);
            String id = METRICS_PREFIX + groupKey + agentIdenty;
            plotNode.setId(id);

            if (!monitoringPlotNode.getPlots().contains(plotNode))
                monitoringPlotNode.getPlots().add(plotNode);

        }

        ArrayList<MonitoringSessionScopePlotNode> result = new ArrayList<MonitoringSessionScopePlotNode>(tempMap.values());
        for (MonitoringSessionScopePlotNode ms : result) {
            MetricRankingProvider.sortPlotNodes(ms.getPlots());
        }
        MetricRankingProvider.sortPlotNodes(result);

        return result;
    }


    private Map<TaskDataDto, List<PlotNode>> getMonitoringPlotNames(Set<Map.Entry<GroupKey, DefaultMonitoringParameters[]>> monitoringParameters, Map<TaskDataDto, List<BigInteger>> monitoringIdsMap) {
        Set<BigInteger> monitoringIds = new HashSet<BigInteger>();
        for (List<BigInteger> mIds : monitoringIdsMap.values()) {
            monitoringIds.addAll(mIds);
        }

        long temp = System.currentTimeMillis();
        List<Object[]> agentIdentifierObjects =
                entityManager.createNativeQuery("select ms.boxIdentifier, ms.systemUnderTestUrl, ms.taskData_id, ms.description  from MonitoringStatistics as ms" +
                        "  where " +
                        " ms.taskData_id in (:taskIds) " +
                        " group by ms.taskData_id, ms.description, boxIdentifier, systemUnderTestUrl")
                        .setParameter("taskIds", monitoringIds)
                        .getResultList();
        log.debug("db call to fetch all MonitoringPlotNames for tests in {} ms (size: {})", System.currentTimeMillis() - temp, agentIdentifierObjects.size());

        Map<TaskDataDto, Set<PlotNode>> resultMap = new HashMap<TaskDataDto, Set<PlotNode>>();

        Set<TaskDataDto> taskSet = monitoringIdsMap.keySet();

        for (Object[] objects : agentIdentifierObjects) {
            BigInteger testId = (BigInteger)objects[2];
            for (TaskDataDto tdd : taskSet) {
                if (monitoringIdsMap.get(tdd).contains(testId)) {
                    if (!resultMap.containsKey(tdd)) {
                        resultMap.put(tdd, new HashSet<PlotNode>());
                    }

                    String description = (String) objects[3];
                    String monitoringId = null;     // Id of particular metric
                    for (Map.Entry<GroupKey, DefaultMonitoringParameters[]> entry : monitoringParameters) {
                        for (DefaultMonitoringParameters dmp : entry.getValue()) {
                            if (dmp.getDescription().equals(description)) {
                                monitoringId = dmp.getId();
                            }
                        }
                    }

                    if (monitoringId == null) {
                        log.warn("Could not find monitoring key for description: '{}' and monitoing task id: '{}'", description, objects[2]);
                        break;
                    }

                    String agentId = objects[0] == null ? objects[1].toString() : objects[0].toString();

                    PlotNode plotNode = new PlotNode();

                    String id = METRICS_PREFIX + tdd.hashCode() + "_" + monitoringId + "_" + agentId;
                    MetricNameDto metricNameDto = new MetricNameDto(tdd, MonitoringIdUtils.getMonitoringMetricId(monitoringId, agentId));
                    metricNameDto.setOrigin(MetricNameDto.Origin.MONITORING);
                    metricNameDto.setMetricDisplayName(description);
                    plotNode.init(id, id, Arrays.asList(metricNameDto));

                    resultMap.get(tdd).add(plotNode);
                    break;
                }
            }
        }

        // set to list
        Map<TaskDataDto, List<PlotNode>> newResultMap = new HashMap<TaskDataDto, List<PlotNode>>();
        for (TaskDataDto taskDataDto: resultMap.keySet()) {
            newResultMap.put(taskDataDto,new ArrayList<PlotNode>(resultMap.get(taskDataDto)));
        }

        return newResultMap;
    }

    private String findMonitoringKey(String description, Set<Map.Entry<GroupKey, DefaultMonitoringParameters[]>> monitoringParameters) {
        for (Map.Entry<GroupKey, DefaultMonitoringParameters[]> entry : monitoringParameters) {
            for (DefaultMonitoringParameters dmp : entry.getValue()) {
                if (dmp.getDescription().equals(description)) {
                    return entry.getKey().getUpperName();
                }
            }
        }
        return null;
    }

    private Map<TaskDataDto, Boolean> isWorkloadStatisticsAvailable(List<TaskDataDto> tests) {

        List<Long> testsIds = new ArrayList<Long>();
        for (TaskDataDto tdd : tests) {
            testsIds.addAll(tdd.getIds());
        }

        long temp = System.currentTimeMillis();
        List<Object[]> objects  = entityManager.createQuery("select tis.taskData.id, count(tis.id) from TimeInvocationStatistics as tis where tis.taskData.id in (:tests)")
                .setParameter("tests", testsIds)
                .getResultList();
        log.debug("db call to check if WorkloadStatisticsAvailable in {} ms (size: {})", System.currentTimeMillis() - temp, objects.size());


        if (objects.isEmpty()) {
            return Collections.EMPTY_MAP;
        }

        Map<TaskDataDto, Integer> tempMap = new HashMap<TaskDataDto, Integer>(tests.size());
        for (TaskDataDto tdd : tests) {
            tempMap.put(tdd, 0);
        }

        for (Object[] object : objects) {
            for (TaskDataDto tdd : tests) {
                if (tdd.getIds().contains((Long) object[1])) {
                    int value = tempMap.get(tdd);
                    tempMap.put(tdd, ++value);
                }
            }
        }

        Map<TaskDataDto, Boolean> resultMap = new HashMap<TaskDataDto, Boolean>(tests.size());
        for (Map.Entry<TaskDataDto, Integer> entry : tempMap.entrySet()) {
            resultMap.put(entry.getKey(), entry.getValue() < entry.getKey().getIds().size());
        }

        return resultMap;
    }


    @Override
    public List<TaskDataDto> getTaskDataForSessions(Set<String> sessionIds) {

        long timestamp = System.currentTimeMillis();

        int havingCount = 0;
        if (webClientProperties.isShowOnlyMatchedTests()) {
            havingCount = sessionIds.size();
        }

        List<Object[]> list = entityManager.createNativeQuery
                (
                        "select taskData.id, commonTests.name, commonTests.description, taskData.taskId , commonTests.clock, commonTests.clockValue, commonTests.termination, taskData.sessionId" +
                                " from " +
                                "( " +
                                "    select test.name, test.description, test.version, test.sessionId, test.taskId, test.clock, test.clockValue, test.termination from " +
                                "    ( " +
                                "        select " +
                                "        l.*, s.name, s.description, s.version " +
                                "        from " +
                                "        (select * from WorkloadTaskData where sessionId in (:sessions)) as l " +
                                "        left outer join " +
                                "        (select * from WorkloadDetails) as s " +
                                "        on l.scenario_id=s.id " +
                                "    ) as test " +
                                "    inner join " +
                                "    ( " +
                                "        select t.* from " +
                                "        ( " +
                                "            select " +
                                "            l.*, s.name, s.description, s.version " +
                                "            from " +
                                "            (select * from WorkloadTaskData where sessionId in (:sessions)) as l " +
                                "            left outer join " +
                                "            (select * from WorkloadDetails) as s " +
                                "            on l.scenario_id=s.id " +
                                "        ) as t " +
                                "        group by " +
                                "        t.termination, t.clock, t.clockValue, t.name, t.description, t.version " +
                                "        having count(t.id)>=" + havingCount +
                                "    ) as testArch " +
                                "    on " +
                                "    test.clock=testArch.clock and " +
                                "    test.clockValue=testArch.clockValue and " +
                                "    test.termination=testArch.termination and " +
                                "    test.name=testArch.name and " +
                                "    test.version=testArch.version " +
                                ") as commonTests " +
                                "left outer join " +
                                "(select * from TaskData where sessionId in (:sessions)) as taskData " +
                                "on " +
                                "commonTests.sessionId=taskData.sessionId and " +
                                "commonTests.taskId=taskData.taskId "
                )
                .setParameter("sessions", sessionIds)
                .getResultList();

        //group tests by description
        HashMap<String, TaskDataDto> map = new HashMap<String, TaskDataDto>(list.size());
        HashMap<String, Integer> mapIds = new HashMap<String, Integer>(list.size());
        for (Object[] testData : list){
            BigInteger id = (BigInteger)testData[0];
            String name = (String) testData[1];
            String description = (String) testData[2];
            String taskId = (String)testData[3];

            // we need clock , and termination here is tool of matching test.
            String clock = (String)testData[4];
            Integer clockValue = (Integer)testData[5];
            String termination = (String) testData[6];

            String sessionId = (String) testData[7];

            int taskIdInt = Integer.parseInt(taskId.substring(5));

            // todo: it should be configurable in future (task about matching strategy).
            String key = description+name+termination+clock+clockValue;
            if (map.containsKey(key)){
                map.get(key).getIds().add(id.longValue());
                map.get(key).getSessionIds().add(sessionId);

                Integer oldValue = mapIds.get(key);
                mapIds.put(key, (oldValue==null ? 0 : oldValue)+taskIdInt);
            }else{
                TaskDataDto taskDataDto = new TaskDataDto(id.longValue(), name, description);
                Set<String> sessionIdList = new HashSet<String>();
                sessionIdList.add(sessionId);
                taskDataDto.setSessionIds(sessionIdList);
                // generate unique to make difference between tests with different matching parameters.
                int uniqueId = CommonUtils.generateUniqueId(name, description, taskId, clock, clockValue, termination);
                taskDataDto.setUniqueId(uniqueId);

                map.put(key, taskDataDto);
                mapIds.put(key, taskIdInt);
            }
        }

        if (map.isEmpty()){
            return Collections.EMPTY_LIST;
        }

        PriorityQueue<Object[]> priorityQueue= new PriorityQueue<Object[]>(mapIds.size(), new Comparator<Object[]>() {
            @Override
            public int compare(Object[] o1, Object[] o2) {
                return ((Comparable)o1[0]).compareTo(o2[0]);
            }
        });

        for (String key : map.keySet()){
            TaskDataDto taskDataDto = map.get(key);
            priorityQueue.add(new Object[]{mapIds.get(key), taskDataDto});
        }

        ArrayList<TaskDataDto> result = new ArrayList<TaskDataDto>(priorityQueue.size());
        while (!priorityQueue.isEmpty()){
            result.add((TaskDataDto)priorityQueue.poll()[1]);
        }

        log.info("For sessions {} was loaded {} tasks for {} ms", new Object[]{sessionIds, result.size(), System.currentTimeMillis() - timestamp});
        return result;
    }

    @Override
    public WebClientProperties getWebClientProperties() {
        return webClientProperties;
    }

    public void setWebClientProperties(WebClientProperties webClientProperties) {
        this.webClientProperties = webClientProperties;
        this.webClientProperties.setUserCommentStoreAvailable(checkIfUserCommentStorageAvailable());
        this.webClientProperties.setTagsStoreAvailable(checkIfTagsStorageAvailable());
    }

    private boolean checkIfUserCommentStorageAvailable() {

        try {
            // even if table is empty we can set user comments
            entityManager.createQuery(
                    "select count(sm) from SessionMetaDataEntity sm")
                    .getSingleResult();
            return true;
        } catch (Exception e) {
            log.warn("Could not access SessionMetaDataTable", e);
        }

        return false;
    }
    
    private boolean checkIfTagsStorageAvailable() {

        try {
            entityManager.createQuery(
                    "select 1 from TagEntity")
                    .getSingleResult();
            return true;
        } catch (Exception e) {
            log.warn("Could not access TagEntity table", e);
        }

        return false;
    }

    public void setFetchUtil(FetchUtil fetchUtil) {
        this.fetchUtil = fetchUtil;
    }
}

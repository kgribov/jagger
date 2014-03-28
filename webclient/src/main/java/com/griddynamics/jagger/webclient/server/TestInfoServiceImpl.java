package com.griddynamics.jagger.webclient.server;

import com.griddynamics.jagger.webclient.client.TestInfoService;
import com.griddynamics.jagger.webclient.client.dto.TaskDataDto;
import com.griddynamics.jagger.webclient.client.dto.TestInfoDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigInteger;
import java.util.*;


public class TestInfoServiceImpl implements TestInfoService {


    private static final Logger log = LoggerFactory.getLogger(NodeInfoServiceImpl.class);
    private EntityManager entityManager;

    @PersistenceContext
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }


    @Override
    public Map<TaskDataDto, Map<String, TestInfoDto>> getTestInfos(Collection<TaskDataDto> taskDataDtos) throws RuntimeException {

        if (taskDataDtos.isEmpty()) {
            return Collections.EMPTY_MAP;
        }

        List<Long> taskDataIds = new ArrayList<Long>();
        for (TaskDataDto taskDataDto : taskDataDtos) {
            taskDataIds.addAll(taskDataDto.getIds());
        }


        long temp = System.currentTimeMillis();
        @SuppressWarnings("all")
        List<Object[]> objectsList = (List<Object[]>)entityManager.createNativeQuery(
                "select wtd.sessionId, wtd.clock, wtd.clockValue, wtd.termination, taskData.id " +
                    "from WorkloadTaskData as wtd join " +
                        "( select  td.id, td.sessionId, td.taskId from TaskData td where td.id in (:taskDataIds) " +
                            ") as taskData " +
                    "on wtd.sessionId=taskData.sessionId and wtd.taskId=taskData.taskId")
                .setParameter("taskDataIds", taskDataIds)
                .getResultList();
        log.debug("Time spent for testInfo fetching for {} tests : {}ms", new Object[]{taskDataDtos.size(), System.currentTimeMillis() - temp});

        Map<TaskDataDto, Map<String, TestInfoDto>> resultMap = new HashMap<TaskDataDto, Map<String, TestInfoDto>>(taskDataDtos.size());

        for (Object[] objects : objectsList) {

            Long taskId = ((BigInteger)objects[4]).longValue();
            String clock = objects[1] + " (" + objects[2] + ')';
            String termination = (String)objects[3];
            String sessionId = (String)objects[0];

            for (TaskDataDto td : taskDataDtos) {
                if (td.getIds().contains(taskId)) {
                    if (!resultMap.containsKey(td)) {
                        resultMap.put(td, new HashMap<String, TestInfoDto>());
                    }

                    TestInfoDto testInfo = new TestInfoDto();
                    testInfo.setClock(clock);
                    testInfo.setTermination(termination);
                    resultMap.get(td).put(sessionId, testInfo);
                    break;
                }
            }
        }

        return resultMap;

    }
}

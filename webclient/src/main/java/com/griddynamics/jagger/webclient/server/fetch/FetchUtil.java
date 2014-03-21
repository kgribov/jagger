package com.griddynamics.jagger.webclient.server.fetch;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/**
 * Created by kgribov on 3/17/14.
 */
public class FetchUtil {
    private EntityManager entityManager;

    @PersistenceContext
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * @return multi map <test-group id, tests ids>
     */
    public Multimap<Long, Long> getTestsInTestGroup(Set<Long> taskIds){
        List<Object[]> parents = getParents(taskIds);
        List<Object[]> groups = getTestGroups(taskIds);

        Multimap testMap = ArrayListMultimap.create();
        for (Object[] test : parents){
            String key = (String)test[1] + (String)test[2];
            testMap.put(key, ((BigInteger) test[0]).longValue());
        }

        Multimap testsByGroupId = ArrayListMultimap.create();
        for (Object[] group : groups){
            String key = (String)group[1] + (String)group[2];
            testsByGroupId.putAll(((BigInteger) group[0]).longValue(), testMap.get(key));
        }

        return testsByGroupId;
    }

    /**
     * @return list of object[] (taskdata id, parent id, session id)
     */
    public List<Object[]> getParents(Set<Long> taskIds){
        return entityManager.createNativeQuery("select taskData.id, workloadData.parentId, taskData.sessionId from TaskData taskData " +
                                               "inner join " +
                                                    "WorkloadData workloadData on taskData.taskId = workloadData.taskId " +
                                                                             " and taskData.sessionId = workloadData.sessionId " +
                                               "where taskData.id in (:ids);")
                                               .setParameter("ids", taskIds).getResultList();
        }

    /**
     * @return list of object[] (taskdata id, taskId, session id)
     */
    public List<Object[]> getTestGroups(Set<Long> taskIds){
        return entityManager.createNativeQuery("select task.id, task.taskId, task.sessionId from TaskData task " +
                                               "inner join " +
                                                    "(select taskData.id, taskData.taskId, workloadData.parentId, taskData.sessionId from TaskData taskData " +
                                                     "inner join WorkloadData workloadData on  taskData.taskId=workloadData.taskId " +
                                                                                          "and taskData.sessionId=workloadData.sessionId  " +
                                                     "where taskData.id in (:ids)) parents " +
                                               "on task.taskId=parents.parentId " +
                                               "and task.sessionId=parents.sessionId;")
                                               .setParameter("ids", taskIds).getResultList();
    }


}

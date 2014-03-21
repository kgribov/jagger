package com.griddynamics.jagger.webclient.server.rules;

import com.griddynamics.jagger.agent.model.DefaultMonitoringParameters;
import com.griddynamics.jagger.monitoring.reporting.GroupKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TreeViewGroupMetricsToNodeRuleProvider {

    private Map<GroupKey, DefaultMonitoringParameters[]> monitoringPlotGroups;

    public Map<GroupKey, DefaultMonitoringParameters[]> getMonitoringPlotGroups() {
        return monitoringPlotGroups;
    }
    public void setMonitoringPlotGroups(Map<GroupKey, DefaultMonitoringParameters[]> monitoringPlotGroups) {
        this.monitoringPlotGroups = monitoringPlotGroups;
    }

    public TreeViewGroupMetricsToNodeRule provide (Map<String,Set<String>> agentNames) {
        List<TreeViewGroupMetricsToNodeRule> result = new ArrayList<TreeViewGroupMetricsToNodeRule>();

        // Create rules to combine default monitor parameters
        if ((agentNames != null) && (agentNames.size() > 0)) {
            for(Map.Entry<GroupKey,DefaultMonitoringParameters[]> groupKeyEntry : monitoringPlotGroups.entrySet()) {
                String metricDisplayName = groupKeyEntry.getKey().getUpperName();

                // group metrics to nodes by DefaultMonitoringParameters and AgentIds
                if (agentNames.containsKey(metricDisplayName)) {
                    for (String agentId : agentNames.get(metricDisplayName)) {
                        String regex = "";
                        for (DefaultMonitoringParameters defaultMonitoringParameters : groupKeyEntry.getValue()) {
                            // not first / first time
                            if (regex.length() != 0) {
                                regex += "|";
                            }
                            else {
                                regex += "^.*(";
                            }
                            regex += defaultMonitoringParameters.getId();
                        }
                        String safeAgentId = agentId.replace("[","\\[").replace("]","\\]").replace("(", "\\(").replace(")","\\)");
                        regex += ").*" + safeAgentId + ".*";

                        result.add(new TreeViewGroupMetricsToNodeRule(Rule.By.ID, metricDisplayName + "_" + agentId,agentId,regex));
                    }
                }
            }
        }

        return TreeViewGroupMetricsToNodeRule.Composer.compose(result);
    }
}

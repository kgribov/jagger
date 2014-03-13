package com.griddynamics.jagger.webclient.server.rules;

import com.griddynamics.jagger.webclient.client.components.control.model.MetricGroupNode;
import com.griddynamics.jagger.webclient.client.components.control.model.MetricNode;
import com.griddynamics.jagger.webclient.client.dto.MetricNameDto;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TreeViewGroupRule extends Rule{

    public TreeViewGroupRule(String id, String displayName, String rule) {
        super(id,displayName,rule);
    }
    public TreeViewGroupRule(String id, String displayName, String rule, List<TreeViewGroupRule> children) {
        super(id,displayName,rule);

        // remove duplicates and sort
        List<TreeViewGroupRule> temp = removeDuplicates(By.ID,children);
        this.children = sort(By.DISPLAY_NAME, temp);
    }

    public <M extends MetricNode> MetricGroupNode<M> filter(By by, String parentId, List<M> metricNodeList) {
        MetricGroupNode<M> result = new MetricGroupNode<M>(displayName);
        boolean returnResult = false;

        // null is required only for root node
        // when no parent available
        String id;
        if (parentId == null) {
            id = this.id;
        }
        else {
            // depth of tree is not limited => be careful with long id. They will concatenate
            // G goes for group
            id = parentId + "G_" + this.id;
        }

        // unique Id for metric group
        result.setId(id);

        // first ask all children to filter
        List<MetricGroupNode> metricGroupNodeListFromChildren = new ArrayList<MetricGroupNode>();
        if (children != null) {
            for (TreeViewGroupRule child : children) {
                MetricGroupNode childResult = child.filter(by,id,metricNodeList);
                if (childResult != null) {
                    metricGroupNodeListFromChildren.add(childResult);
                }
            }
            if (metricGroupNodeListFromChildren.size() > 0) {
                result.setMetricGroupNodeList(metricGroupNodeListFromChildren);
                returnResult = true;
            }
        }

        // apply own filter
        if (rule != null) {
            List<M> metricsPerRule = new ArrayList<M>();
            Iterator<M> i = metricNodeList.iterator();
            while (i.hasNext()) {
                M metricNode = i.next();

                // match
                String metric;
                // node can contain more than single metric
                // current strategy: if at least one metric match => add node to group
                for (MetricNameDto metricNameDto : metricNode.getMetricNameDtoList()) {
                    if (by == By.DISPLAY_NAME) {
                        metric = metricNameDto.getMetricDisplayName();
                    }
                    else {
                        metric = metricNameDto.getMetricName();
                    }
                    if (metric.matches(rule)) {
                        metricsPerRule.add(metricNode);
                        i.remove();
                        break;
                    }
                }
            }
            if (metricsPerRule.size() > 0) {
                result.setMetrics(metricsPerRule);
                returnResult = true;
            }
        }

        if (returnResult) {
            return result;
        }
        else {
            return null;
        }
    }

    public List<TreeViewGroupRule> getChildren() {
        return children;
    }

    private List<TreeViewGroupRule> children = null;

}

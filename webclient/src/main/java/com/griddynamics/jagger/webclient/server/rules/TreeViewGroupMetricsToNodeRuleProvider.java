package com.griddynamics.jagger.webclient.server.rules;

import java.util.ArrayList;
import java.util.List;

public class TreeViewGroupMetricsToNodeRuleProvider {

    public static TreeViewGroupMetricsToNodeRule provide () {
        List<TreeViewGroupMetricsToNodeRule> result = new ArrayList<TreeViewGroupMetricsToNodeRule>();

        // Left here as examples
//        result.add(new TreeViewGroupMetricsToNodeRule("mysql_bytesMN","MySQL Bytes","^mysql_bytes.*"));

        return TreeViewGroupMetricsToNodeRule.Composer.compose(result);
    }
}

package com.griddynamics.jagger.webclient.server.rules;

import java.util.ArrayList;
import java.util.List;

public class TreeViewGroupRuleProvider {

    public static TreeViewGroupRule provide (String rootId, String rootName) {

//        Left here as examples
//        List<TreeViewGroupRule> mySQL_SecondLevelFilters = new ArrayList<TreeViewGroupRule>();
//        mySQL_SecondLevelFilters.add(new TreeViewGroupRule("innodb", "InnoDB", "^MySQL InnoDB .*"));
//        mySQL_SecondLevelFilters.add(new TreeViewGroupRule("bytes", "Bytes", "^MySQL Bytes .*"));
//        mySQL_SecondLevelFilters.add(new TreeViewGroupRule("quest", "Questions", "^MySQL Questions .*"));
//        TreeViewGroupRule mySQL_FirstLevelFilter = new TreeViewGroupRule("mysql","MySQL","^MySQL .*",mySQL_SecondLevelFilters);
//        TreeViewGroupRule JMX_FirstLevelFilter = new TreeViewGroupRule("jmx","JMX","^JMX .*");

        // Filter for Jagger main metrics
        TreeViewGroupRule mainParams_FirstLevelFilter = new TreeViewGroupRule("main","Main parameters",
                "(^Throughput, tps$|^Throughput$|^Latency, sec$|^Latency$" +
                "|^Iterations, samples$|^Success rate$|^Duration, sec$|^Latency\\s\\S+\\s%$|^Time Latency Percentile$)");

        List<TreeViewGroupRule> firstLevelFilters = new ArrayList<TreeViewGroupRule>();
        firstLevelFilters.add(mainParams_FirstLevelFilter);

        // Root filter - will match all metrics
        return new TreeViewGroupRule(rootId,rootName,".*",firstLevelFilters);
    }
}

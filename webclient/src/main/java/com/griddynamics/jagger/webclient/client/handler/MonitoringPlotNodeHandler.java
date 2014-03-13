package com.griddynamics.jagger.webclient.client.handler;

import com.griddynamics.jagger.webclient.client.components.control.model.MetricNode;
import com.griddynamics.jagger.webclient.client.components.control.model.MonitoringPlotNode;
import com.sencha.gxt.widget.core.client.event.CheckChangeEvent;
import com.sencha.gxt.widget.core.client.tree.Tree;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: amikryukov
 * Date: 12/11/13
 */
public class MonitoringPlotNodeHandler extends TreeAwareHandler<MonitoringPlotNode> {
    @Override
    public void onCheckChange(CheckChangeEvent<MonitoringPlotNode> event) {

        Set<MetricNode> nodes = new LinkedHashSet<MetricNode>();
        nodes.addAll(event.getItem().getPlots());

        if (Tree.CheckState.CHECKED.equals(event.getChecked())) {
            testPlotFetcher.fetchPlots(nodes);
        } else {
            testPlotFetcher.removePlots(nodes);
        }
    }
}

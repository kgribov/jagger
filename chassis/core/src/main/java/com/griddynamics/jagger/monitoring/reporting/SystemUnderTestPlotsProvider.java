/*
 * Copyright (c) 2010-2012 Grid Dynamics Consulting Services, Inc, All Rights Reserved
 * http://www.griddynamics.com
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of
 * the GNU Lesser General Public License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.griddynamics.jagger.monitoring.reporting;

import com.google.common.collect.*;
import com.griddynamics.jagger.agent.model.*;
import com.griddynamics.jagger.monitoring.MonitoringParameterBean;
import com.griddynamics.jagger.monitoring.model.MonitoringStatistics;
import com.griddynamics.jagger.reporting.MappedReportProvider;
import com.griddynamics.jagger.reporting.ReportingContext;
import com.griddynamics.jagger.reporting.chart.ChartHelper;
import com.griddynamics.jagger.util.Pair;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.renderers.JCommonDrawableRenderer;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * @author Alexey Kiselyov
 *         Date: 26.07.11
 */
public class SystemUnderTestPlotsProvider extends AbstractMonitoringReportProvider<String> {
    private Logger log = LoggerFactory.getLogger(SystemUnderTestPlotsProvider.class);

    private DynamicPlotGroups plotGroups;
    private boolean showPlotsByGlobal;
    private boolean showPlotsByBox;
    private boolean showPlotsBySuT;

    private boolean enable;
    private Map<String, List<MonitoringReporterData>> taskPlots;

    private DetailStatistics statistics = null;

    @Override
    public void clearCache() {
        super.clearCache();

        taskPlots = null;
    }

    @Override
    public JRDataSource getDataSource(String taskId) {
        log.debug("Report for task with id {} requested", taskId);
        if (!this.enable) {
            return new JRBeanCollectionDataSource(Collections.emptySet());
        }

        if (taskPlots == null) {
            log.debug("Initing task plots");
            taskPlots = createTaskPlots();
        }

        loadMonitoringMap();

        log.debug("Loading data for task id {}", taskId);
        List<MonitoringReporterData> data = taskPlots.get(taskId);

        if (data == null) {
            log.debug("Data not found for task {}", taskId);
            String monitoringTaskId = relatedMonitoringTask(taskId);
            log.debug("Related task {}", monitoringTaskId);
            data = taskPlots.get(monitoringTaskId);
        }

        if (data == null) {
            log.warn("SuT plots not found for task with id {}", taskId);
            log.warn("Check that MonitoringAggregator is configured!!!");
        }

        return new JRBeanCollectionDataSource(data);
    }

    public Map<String, List<MonitoringReporterData>> createTaskPlots() {
        log.info("BEGIN: Create task plots");

        Map<String, List<MonitoringReporterData>> taskPlots = new LinkedHashMap<String, List<MonitoringReporterData>>();
        DetailStatistics detailStatistics = getStatistics();

        for (String taskId : detailStatistics.findTaskIds()) {
            log.debug("    Create task plots for task '{}'", taskId);

            List<MonitoringReporterData> plots = new LinkedList<MonitoringReporterData>();
            for (GroupKey groupName : plotGroups.getPlotGroups().keySet()) {
                log.debug("        Create task plots for group '{}'", groupName);

                if (showPlotsByGlobal) {
                    log.debug("            Create global task plots");

                    XYSeriesCollection chartsCollection = new XYSeriesCollection();
                    for (MonitoringParameter parameterId : plotGroups.getPlotGroups().get(groupName)) {
                        log.debug("                Create global task plots for parameter '{}'", parameterId);

                        MonitoringParameterBean param = MonitoringParameterBean.copyOf(parameterId);
                        List<MonitoringStatistics> statistics = detailStatistics.findGlobalStatistics(taskId, param);
                        if (!statistics.isEmpty()) {
                            XYSeries values = new XYSeries(param.getDescription());
                            for (MonitoringStatistics monitoringStatistics : statistics) {
                                values.add(monitoringStatistics.getTime(), monitoringStatistics.getAverageValue());
                            }
                            if (values.isEmpty()) {
                                values.add(0, 0);
                            }
                            chartsCollection.addSeries(values);
                        }
                    }

                    log.debug("group name \n{} \nparams {}]\n", groupName, Lists.newArrayList(plotGroups.getPlotGroups().get(groupName)));

                    Pair<String, XYSeriesCollection> pair = ChartHelper.adjustTime(chartsCollection, null);

                    chartsCollection = pair.getSecond();

                    if (chartsCollection.getSeriesCount() > 0) {
                        JFreeChart chart = ChartHelper.createXYChart(null, chartsCollection, "Time (" + pair.getFirst() + ")",
                                groupName.getLeftName(), 3, 2, ChartHelper.ColorTheme.LIGHT);
                        MonitoringReporterData monitoringReporterData = new MonitoringReporterData();
                        monitoringReporterData.setParameterName(groupName.getUpperName());
                        monitoringReporterData.setTitle(groupName.getUpperName());
                        monitoringReporterData.setPlot(new JCommonDrawableRenderer(chart));
                        plots.add(monitoringReporterData);
                    }
                }

                if (showPlotsByBox) {
                    log.debug("            Create box task plots");

                    for (String boxIdentifier : detailStatistics.findBoxIdentifiers(taskId)) {
                        log.debug("                Create box task plots for box '{}'", boxIdentifier);

                        XYSeriesCollection chartsCollection = new XYSeriesCollection();
                        for (MonitoringParameter parameterId : plotGroups.getPlotGroups().get(groupName)) {
                            log.debug("                    Create box task plots for parameter '{}'", parameterId);

                            MonitoringParameterBean param = MonitoringParameterBean.copyOf(parameterId);
                            List<MonitoringStatistics> statistics = detailStatistics.findBoxStatistics(taskId, param, boxIdentifier);
                            if (!statistics.isEmpty()) {
                                XYSeries values = new XYSeries(param.getDescription());
                                for (MonitoringStatistics monitoringStatistics : statistics) {
                                    values.add(monitoringStatistics.getTime(), monitoringStatistics.getAverageValue());
                                }
                                if (values.isEmpty()) {
                                    values.add(0, 0);
                                }
                                chartsCollection.addSeries(values);
                            }
                        }

                        log.debug("group name \n{} \nparams {}]\n", groupName, Lists.newArrayList(plotGroups.getPlotGroups().get(groupName)));

                        Pair<String, XYSeriesCollection> pair = ChartHelper.adjustTime(chartsCollection, null);

                        chartsCollection = pair.getSecond();

                        if (chartsCollection.getSeriesCount() > 0) {
                            JFreeChart chart = ChartHelper.createXYChart(null, chartsCollection, "Time (" + pair.getFirst() + ")",
                                    groupName.getLeftName(), 3, 2, ChartHelper.ColorTheme.LIGHT);
                            MonitoringReporterData monitoringReporterData = new MonitoringReporterData();
                            monitoringReporterData.setParameterName(groupName.getUpperName());
                            monitoringReporterData.setTitle(groupName.getUpperName() + " on " + boxIdentifier);
                            monitoringReporterData.setPlot(new JCommonDrawableRenderer(chart));
                            plots.add(monitoringReporterData);
                        }
                    }
                }

                if (showPlotsBySuT) {
                    log.debug("            Create sut task plots");

                    for (String sutUrl : detailStatistics.findSutUrls(taskId)) {
                        log.debug("                Create sut task plots for sut '{}'", sutUrl);

                        XYSeriesCollection chartsCollection = new XYSeriesCollection();
                        for (MonitoringParameter parameterId : plotGroups.getPlotGroups().get(groupName)) {
                            log.debug("                    Create sut task plots for parameter '{}'", parameterId);

                            MonitoringParameterBean param = MonitoringParameterBean.copyOf(parameterId);
                            List<MonitoringStatistics> statistics = detailStatistics.findSutStatistics(taskId, param, sutUrl);
                            if (!statistics.isEmpty()) {
                                XYSeries values = new XYSeries(param.getDescription());
                                for (MonitoringStatistics monitoringStatistics : statistics) {
                                    values.add(monitoringStatistics.getTime(), monitoringStatistics.getAverageValue());
                                }
                                if (values.isEmpty()) {
                                    values.add(0, 0);
                                }
                                chartsCollection.addSeries(values);
                            }
                        }

                        log.debug("group name \n{} \nparams {}]\n", groupName, Lists.newArrayList(plotGroups.getPlotGroups().get(groupName)));

                        Pair<String, XYSeriesCollection> pair = ChartHelper.adjustTime(chartsCollection, null);

                        chartsCollection = pair.getSecond();

                        if (chartsCollection.getSeriesCount() > 0) {
                            JFreeChart chart = ChartHelper.createXYChart(null, chartsCollection, "Time (" + pair.getFirst() + ")",
                                    groupName.getLeftName(), 3, 2, ChartHelper.ColorTheme.LIGHT);
                            MonitoringReporterData monitoringReporterData = new MonitoringReporterData();
                            monitoringReporterData.setParameterName(groupName.getUpperName());
                            monitoringReporterData.setTitle(groupName.getUpperName() + " on " + sutUrl);
                            monitoringReporterData.setPlot(new JCommonDrawableRenderer(chart));
                            plots.add(monitoringReporterData);
                        }
                    }
                }
            }

            taskPlots.put(taskId, plots);
        }

        clearStatistics();

        log.info("END: Create task plots");

        return taskPlots;
    }

    public DetailStatistics getStatistics() {
        if (statistics == null) {
            String sessionId = getSessionIdProvider().getSessionId();

            @SuppressWarnings("unchecked")
            List<MonitoringStatistics> st = getHibernateTemplate().find(
                    "select new MonitoringStatistics(" +
                            "ms.boxIdentifier, ms.systemUnderTestUrl, ms.sessionId, ms.taskData, ms.time, ms.parameterId, ms.averageValue" +
                            ") " +
                            "from MonitoringStatistics ms where ms.sessionId=? " +
                            "order by ms.taskData.number asc, ms.time asc", sessionId);

            statistics = new DetailStatistics(sessionId, st);
        }
        return statistics;
    }

    public void clearStatistics() {
        statistics = null;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public DynamicPlotGroups getPlotGroups() {
        return plotGroups;
    }

    @Required
    public void setPlotGroups(DynamicPlotGroups plotGroups) {
        this.plotGroups = plotGroups;
    }

    public boolean isShowPlotsByGlobal() {
        return showPlotsByGlobal;
    }

    @Required
    public void setShowPlotsByGlobal(boolean showPlotsByGlobal) {
        this.showPlotsByGlobal = showPlotsByGlobal;
    }

    public boolean isShowPlotsByBox() {
        return showPlotsByBox;
    }

    @Required
    public void setShowPlotsByBox(boolean showPlotsByBox) {
        this.showPlotsByBox = showPlotsByBox;
    }

    public boolean isShowPlotsBySuT() {
        return showPlotsBySuT;
    }

    @Required
    public void setShowPlotsBySuT(boolean showPlotsBySuT) {
        this.showPlotsBySuT = showPlotsBySuT;
    }

    public static class MonitoringReporterData {
        private String parameterName;
        private String title;
        private JCommonDrawableRenderer plot;

        public String getParameterName() {
            return this.parameterName;
        }

        public void setParameterName(String parameterName) {
            this.parameterName = parameterName;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public JCommonDrawableRenderer getPlot() {
            return this.plot;
        }

        public void setPlot(JCommonDrawableRenderer plot) {
            this.plot = plot;
        }
    }
}

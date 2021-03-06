package com.griddynamics.jagger.webclient.client.components;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.googlecode.gflot.client.DataPoint;
import com.googlecode.gflot.client.Series;
import com.googlecode.gflot.client.SeriesHandler;
import com.googlecode.gflot.client.SimplePlot;
import com.griddynamics.jagger.dbapi.dto.PlotSingleDto;
import com.griddynamics.jagger.dbapi.dto.PointDto;
import com.griddynamics.jagger.dbapi.model.AbstractIdentifyNode;
import com.griddynamics.jagger.dbapi.model.LegendNode;
import com.sencha.gxt.core.client.ValueProvider;
import com.sencha.gxt.core.client.util.Format;
import com.sencha.gxt.data.shared.ModelKeyProvider;
import com.sencha.gxt.data.shared.TreeStore;
import com.sencha.gxt.widget.core.client.tips.QuickTip;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of AbstractTree that represents interactive legend as tree.
 */
public class LegendTree extends AbstractTree<AbstractIdentifyNode, LegendTree.CellData> {

    /**
     * Plot that would controlled with Legend tree
     */
    private SimplePlot plot;

    /**
     * null if it is metric, not null if it is trend */
    // todo : JFG-803 simplify trends plotting mechanism
    private List<Integer> trendSessionIds;

    /**
     * Plots panel where plot is situated
     */
    private PlotsPanel plotsPanel;


    private final static ValueProvider<AbstractIdentifyNode, CellData> VALUE_PROVIDER =  new LegendTreeValueProvider();

    /**
     * Constructor matches super class
     *
     * @param trendSessionIds - set null if it is metric, sorted list of session ids if it is trend
     */
    public LegendTree(SimplePlot plot, PlotsPanel plotsPanel, List<Integer> trendSessionIds) {
        super(
                new TreeStore<AbstractIdentifyNode>(new ModelKeyProvider<AbstractIdentifyNode>() {
                    @Override
                    public String getKey(AbstractIdentifyNode item) {
                        return item.getId();
                    }
                }),
                VALUE_PROVIDER);
        this.plot = plot;
        this.plotsPanel = plotsPanel;
        this.trendSessionIds = trendSessionIds;

        this.setAutoExpand(true);
        this.setCell(new LegendNodeCell());
        this.setSelectionModel(null);

        // register tip manager for tree
        QuickTip qt = new QuickTip(this);
        qt.setShadow(false);
    }

    @Override
    protected void check(AbstractIdentifyNode item, CheckState state) {
        noRedrawCheck(item, state);
        redrawPlot();
    }

    /**
     * Check all items in tree
     */
    public void checkAll() {
        for (AbstractIdentifyNode node : store.getRootItems()) {
            setChecked(node, CheckState.CHECKED);
        }
    }

    /**
     * Adds or removes lines without redrawing plot. Changes can't be seen.
     * If group item was checked, we want to redraw plot once (instead of firing redrawing for each line).
     *
     * @param item chosen item
     * @param state check state
     */
    private void noRedrawCheck(AbstractIdentifyNode item, CheckState state) {

        PlotSingleDto plotSingleDto = null;

        if (item instanceof LegendNode) {
            plotSingleDto = ((LegendNode) item).getLine();
        }


        if (plotSingleDto != null) {

            if (state == CheckState.CHECKED) {

                Series series = Series.create().setId(item.getId()).setColor(plotSingleDto.getColor()).setLabel(plotSingleDto.getLegend());
                SeriesHandler sh = plot.getModel().addSeries(series);
                for (PointDto point: plotSingleDto.getPlotData()) {
                    if (trendSessionIds != null) {
                        // it is trend
                        sh.add(DataPoint.of(trendSessionIds.indexOf((int) point.getX()), point.getY()));
                    } else {
                        sh.add(DataPoint.of(point.getX(), point.getY()));
                    }
                }

            } else if (state == CheckState.UNCHECKED) {

                // remove curve from view
                JsArray<Series> seriesArray = plot.getModel().getSeries();
                int k;
                for (k = 0; k < seriesArray.length(); k++) {
                    Series curSeries = seriesArray.get(k);
                    // label used as id
                    if (curSeries.getId().equals(item.getId())) {
                        // found
                        plot.getModel().removeSeries(k);
                        break;
                    }
                }
            }
        } else {
            for (AbstractIdentifyNode child : store.getAllChildren(item)) {
                noRedrawCheck(child, state);
            }
        }
    }

    /**
     * Redraw plot with specific axis ranges
     */
    private void redrawPlot() {
        if (!plotsPanel.isEmpty()) {
            double minXVisible = plotsPanel.getMinXAxisVisibleValue();
            double maxXVisible = plotsPanel.getMaxXAxisVisibleValue();

            if (plot.isAttached()) {
                double minYVisible = plot.getAxes().getY().getMinimumValue();
                double maxYVisible = plot.getAxes().getY().getMaximumValue();

                // save y axis range for plot from very start
                plot.getOptions().getYAxisOptions().setMinimum(minYVisible).setMaximum(maxYVisible);
            }

            // set x axis in range as all other plots
            plot.getOptions().getXAxisOptions().setMinimum(minXVisible).setMaximum(maxXVisible);
            plot.redraw();
        }
    }


    /**
     * Returns list of checked lines
     * @return list of PlotSingleDto objects
     */
    public List<PlotSingleDto> getListOfSelectedLines() {
        List<PlotSingleDto> result = new ArrayList<PlotSingleDto>();
        for (AbstractIdentifyNode node : store.getAll()) {
            if ( (isChecked(node)) && (node instanceof LegendNode) ) {
                result.add(((LegendNode) node).getLine());
            }
        }
        return result;
    }


    /**
     * Model of cell to display.
     */
    protected static class CellData {

        private String displayName;
        private String color;

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColor() {
            return color;
        }
    }

    /**
     * Cell to be displayed with specific cell model (CellData)
     */
    private static class LegendNodeCell extends AbstractCell<CellData> {

        @Override
        public void render(Context context, CellData value, SafeHtmlBuilder sb) {
            sb.appendHtmlConstant(
                    (value.getColor() != null ? "<font color=\'" + value.getColor() + "\'>&#9604;&#9604;</font>" : "") +
                            "<font qtip='" + Format.htmlEncode(value.getDisplayName()) + "'>  " + value.getDisplayName() + "</font>");
        }
   }

    /**
     * Value provider to set up cell model depending on node model
     */
    private static class LegendTreeValueProvider implements ValueProvider<AbstractIdentifyNode, CellData> {

        @Override
        public CellData getValue(AbstractIdentifyNode object) {

            CellData cellData = new CellData();
            cellData.setDisplayName(object.getDisplayName());
            if (object instanceof LegendNode) {
                cellData.setColor(((LegendNode) object).getLine().getColor());
            }
            return cellData;
        }

        @Override
        public void setValue(AbstractIdentifyNode object, CellData value) {
            object.setDisplayName(value.getDisplayName());
        }

        @Override
        public String getPath() {
            return "legend";
        }
    }



}

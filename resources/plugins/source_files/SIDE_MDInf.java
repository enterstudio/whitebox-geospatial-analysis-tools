/*
 * Copyright (C) 2014 Jan Seibert (jan.seibert@geo.uzh.ch) and 
 * Marc Vis (marc.vis@geo.uzh.ch)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package plugins;

import java.util.Date;
import whitebox.geospatialfiles.WhiteboxRaster;
import whitebox.geospatialfiles.WhiteboxRasterBase;
import whitebox.interfaces.WhiteboxPlugin;
import whitebox.interfaces.WhiteboxPluginHost;

/**
 * This tool is used to compute the side separated contributions to a stream (Grabs et al., 2010) using the MDInf algorithm (Seibert and McGlynn, 2007).
 * @author Dr. John Lindsay email: jlindsay@uoguelph.ca
 */
public class SIDE_MDInf implements WhiteboxPlugin {

    private WhiteboxPluginHost myHost = null;
    private String[] args;
    
    double pi = Math.PI;
    
    WhiteboxRaster dem;
    WhiteboxRaster flowAccumulation;
    WhiteboxRaster streams;
    WhiteboxRaster flowAccumTotal;
    WhiteboxRaster flowAccumRight;
    WhiteboxRaster flowAccumLeft;
    
    double caThreshold;
    
    int[] xd = new int[]{0, -1, -1, -1, 0, 1, 1, 1};
    int[] yd = new int[]{-1, -1, 0, 1, 1, 1, 0, -1};
    double[] dd = new double[]{1, Math.sqrt(2), 1, Math.sqrt(2), 1, Math.sqrt(2), 1, Math.sqrt(2)};
    
    double gridRes = 1;

    private enum Side {
        UNKNOWN, RIGHT, LEFT;
    }
    
    /**
     * Used to retrieve the plugin tool's name. This is a short, unique name containing no spaces.
     * @return String containing plugin name.
     */
    @Override
    public String getName() {
        return "SIDE_MDInf";
    }
    /**
     * Used to retrieve the plugin tool's descriptive name. This can be a longer name (containing spaces) and is used in the interface to list the tool.
     * @return String containing the plugin descriptive name.
     */
    @Override
    public String getDescriptiveName() {
    	return "SIDE (MDInf)";
    }
    /**
     * Used to retrieve a short description of what the plugin tool does.
     * @return String containing the plugin// s description.
     */
    @Override
    public String getToolDescription() {
    	return "Applies the SIDE algorithm (using the MDInf flow accumulation).";
    }
    /**
     * Used to identify which toolboxes this plugin tool should be listed in.
     * @return Array of Strings.
     */
    @Override
    public String[] getToolbox() {
    	String[] ret = { "FlowAccum" };
    	return ret;
    }
    /**
     * Sets the WhiteboxPluginHost to which the plugin tool is tied. This is the class
     * that the plugin will send all feedback messages, progress updates, and return objects.
     * @param host The WhiteboxPluginHost that called the plugin tool.
     */
    @Override
    public void setPluginHost(WhiteboxPluginHost host) {
        myHost = host;
    }
    /**
     * Used to communicate feedback pop-up messages between a plugin tool and the main Whitebox user-interface.
     * @param feedback String containing the text to display.
     */
    private void showFeedback(String message) {
        if (myHost != null) {
            myHost.showFeedback(message);
        } else {
            System.out.println(message);
        }
    }
    /**
     * Used to communicate a return object from a plugin tool to the main Whitebox user-interface.
     * @return Object, such as an output WhiteboxRaster.
     */
    private void returnData(Object ret) {
        if (myHost != null) {
            myHost.returnData(ret);
        }
    }

    private int previousProgress = 0;
    private String previousProgressLabel = "";
    /**
     * Used to communicate a progress update between a plugin tool and the main Whitebox user interface.
     * @param progressLabel A String to use for the progress label.
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(String progressLabel, int progress) {
        if (myHost != null && ((progress != previousProgress) || 
                (!progressLabel.equals(previousProgressLabel)))) {
            myHost.updateProgress(progressLabel, progress);
        }
        previousProgress = progress;
        previousProgressLabel = progressLabel;
    }
    /**
     * Used to communicate a progress update between a plugin tool and the main Whitebox user interface.
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(int progress) {
        if (myHost != null && progress != previousProgress) {
            myHost.updateProgress(progress);
        }
        previousProgress = progress;
    }
    /**
     * Sets the arguments (parameters) used by the plugin.
     * @param args An array of string arguments.
     */ 
    @Override
    public void setArgs(String[] args) {
        this.args = args.clone();
    }
    
    private boolean cancelOp = false;
    /**
     * Used to communicate a cancel operation from the Whitebox GUI.
     * @param cancel Set to true if the plugin should be canceled.
     */
    @Override
    public void setCancelOp(boolean cancel) {
        cancelOp = cancel;
    }
    
    private void cancelOperation() {
        showFeedback("Operation cancelled.");
        updateProgress("Progress: ", 0);
    }
    
    private boolean amIActive = false;
    /**
     * Used by the Whitebox GUI to tell if this plugin is still running.
     * @return a boolean describing whether or not the plugin is actively being used.
     */
    @Override
    public boolean isActive() {
        return amIActive;
    }
    
    /**
     * Used to execute this plugin tool.
     */
    @Override
    public void run() {
        amIActive = true;

        String demHeader = null;
        String flowAccumulationHeader = null;
        String streamsHeader = null;
        double mdInfPower = 1;
        String outputType = null;
        String flowAccumTotalHeader = null;
        String flowAccumRightHeader = null;
        String flowAccumLeftHeader = null;

        double initialValue;
        
        double z;
        
        int numRows;
        int numCols;
        
        double noData;
        
        float progress = 0;
        
        if (args.length <= 0) {
            showFeedback("Plugin parameters have not been set.");
            return;
        }
        
        for (int i = 0; i < args.length; i++) {
            if (i == 0) {
                demHeader = args[i];
            } else if (i == 1) {
                flowAccumulationHeader = args[i];
            } else if (i == 2) {
                streamsHeader = args[i];
            } else if (i == 3) {
                mdInfPower = Double.parseDouble(args[i]);
            } else if (i == 4) {
                outputType = args[i].toLowerCase();
            } else if (i == 5) {
                caThreshold = Double.parseDouble(args[i]);
            } else if (i == 6) {
                flowAccumTotalHeader = args[i];
            } else if (i == 7) {
                flowAccumRightHeader = args[i];
            } else if (i == 8) {
                flowAccumLeftHeader = args[i];
            }
        }

        // check to see that the inputHeader and outputHeader are not null.
        if ((demHeader == null) || (flowAccumulationHeader == null) || (streamsHeader == null) || (flowAccumTotalHeader == null) || (flowAccumRightHeader == null) || (flowAccumLeftHeader == null)) {
            showFeedback("One or more of the input parameters have not been set properly.");
            return;
        }

        try {
            dem = new WhiteboxRaster(demHeader, "r");
            flowAccumulation = new WhiteboxRaster(flowAccumulationHeader, "r");
            streams = new WhiteboxRaster(streamsHeader, "r");
            
            numRows = dem.getNumberRows();
            numCols = dem.getNumberColumns();
            noData = dem.getNoDataValue();
            gridRes = dem.getCellSizeX();
                    
            flowAccumTotal = new WhiteboxRaster(flowAccumTotalHeader, "rw", demHeader, WhiteboxRaster.DataType.FLOAT, 1);
            flowAccumTotal.setPreferredPalette("blueyellow.pal");
            flowAccumTotal.setDataScale(WhiteboxRasterBase.DataScale.CONTINUOUS);
            flowAccumTotal.setZUnits("dimensionless");
            
            flowAccumRight = new WhiteboxRaster(flowAccumRightHeader, "rw", demHeader, WhiteboxRaster.DataType.FLOAT, 1);
            flowAccumRight.setPreferredPalette("blueyellow.pal");
            flowAccumRight.setDataScale(WhiteboxRasterBase.DataScale.CONTINUOUS);
            flowAccumRight.setZUnits("dimensionless");
            
            flowAccumLeft = new WhiteboxRaster(flowAccumLeftHeader, "rw", demHeader, WhiteboxRaster.DataType.FLOAT, 1);
            flowAccumLeft.setPreferredPalette("blueyellow.pal");
            flowAccumLeft.setDataScale(WhiteboxRasterBase.DataScale.CONTINUOUS);
            flowAccumLeft.setZUnits("dimensionless");

            updateProgress("Loop 1 of 2:", 0);
            for (int row = 0; row < numRows; row++) {
                for (int col = 0; col < numCols; col++) {
                    z = dem.getValue(row, col);
                    if (z != noData) {
                        flowAccumTotal.setValue(row, col, 0);
                        flowAccumRight.setValue(row, col, 0);
                        flowAccumLeft.setValue(row, col, 0);
                    } else {
                        flowAccumTotal.setValue(row, col, noData);
                        flowAccumRight.setValue(row, col, noData);
                        flowAccumLeft.setValue(row, col, noData);
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (numRows - 1));
                updateProgress("", (int) progress);
            }

            updateProgress("Loop 2 of 2:", 0);
            switch (outputType) {
                case "specific catchment area (sca)":
                    initialValue = gridRes;
                    caThreshold = caThreshold * gridRes;
                    break;
                case "total catchment area":
                    initialValue = gridRes * gridRes;
                    caThreshold = caThreshold * gridRes * gridRes;
                    break;
                default: // case "Number of upslope grid cells"
                    initialValue = 1;
                    break;
            }
            
            for (int row = 0; row < numRows; row++) {
                for (int col = 0; col < numCols; col++) {
                    if (streams.getValue(row, col) > 0) {
                        flowAccumTotal.setValue(row, col, initialValue - caThreshold);
                        flowAccumRight.setValue(row, col, (initialValue - caThreshold) / 2);
                        flowAccumLeft.setValue(row, col, (initialValue - caThreshold) / 2);
                        for (int c = 0; c < 8; c++) {
                            MDInfAccum(row + yd[c], col + xd[c], row, col, (c + 4) % 8, mdInfPower, noData);
                        }
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (float) (100f * row / (numRows - 1));
                updateProgress("", (int) progress);
            }

            flowAccumTotal.addMetadataEntry("Created by the " + getDescriptiveName() + " tool.");
            flowAccumTotal.addMetadataEntry("Created on " + new Date());
            
            flowAccumRight.addMetadataEntry("Created by the " + getDescriptiveName() + " tool.");
            flowAccumRight.addMetadataEntry("Created on " + new Date());
            
            flowAccumLeft.addMetadataEntry("Created by the " + getDescriptiveName() + " tool.");
            flowAccumLeft.addMetadataEntry("Created on " + new Date());
            
            dem.close();
            flowAccumulation.close();
            streams.close();
            flowAccumTotal.close();
            flowAccumRight.close();
            flowAccumLeft.close();

            // returning a header file string displays the image.
            returnData(flowAccumTotalHeader);

        } catch (Exception e) {
            showFeedback(e.getMessage());
        } finally {
            updateProgress("Progress: ", 0);
            // tells the main application that this process is completed.
            amIActive = false;
            myHost.pluginComplete();
        }
    }
    
    private void MDInfAccum(int fromRow, int fromCol, int toStreamRow, int toStreamCol, int flowDirection, double hExp, double noData) {
       
        double z = dem.getValue(fromRow, fromCol);
        double flowAccumVal = flowAccumulation.getValue(fromRow, fromCol);
        int i, ii;
        double p1, p2;
        double z1, z2;
        double nx, ny, nz;
        double hr, hs;
        double[] rFacet = new double[8];
        double[] sFacet = new double[]{noData, noData, noData, noData, noData, noData, noData, noData};

        double[] valley = new double[8];
        double[] portion = new double[8];
        double valleySum = 0;
        double valleyMax = 0;
        int iMax = 0;
        int a, b;
        int c;
        
        Side side;

        if (z == noData) {
            return;
        }
        
        if (streams.getValue(fromRow, fromCol) > 0) {
            // D8
            double slope;
            double maxSlope = Double.MIN_VALUE;
            int flowDir = 255;

            // Find the neighbour with the steepest slope
            for (c = 0; c < 8; c++){
                a = fromCol + xd[c];
                b = fromRow + yd[c];
                z1 = dem.getValue(b, a);
                if ((z > z1) && (z1 != noData)) {
                    slope = (z - z1) / dd[c];
                    if (slope > maxSlope) {
                        maxSlope = slope;
                        flowDir = c;
                    }
                }
            }

            // Update neighbours (actually only the steepest slope neighbour)
            for (c = 0; c < 8; c++){
                a = fromCol + xd[c];
                b = fromRow + yd[c];
                z1 = dem.getValue(b, a);
                if ((z > z1) && (z1 != noData)) {
                    if ((c == flowDir) & (a == toStreamCol) & (b == toStreamRow)) {
                        flowAccumTotal.incrementValue(b, a, caThreshold);
                        flowAccumRight.incrementValue(b, a, caThreshold / 2);
                        flowAccumLeft.incrementValue(b, a, caThreshold / 2);
                    }
                }
            }
        } else {
            // Compute slope and direction for each of the triangular facets
            for (c = 0; c < 8; c++){
                i = c;
                ii = (i + 1) % 8;

                p1 = dem.getValue(fromRow + yd[i], fromCol + xd[i]);
                p2 = dem.getValue(fromRow + yd[ii], fromCol + xd[ii]);
                if ((p1 != noData) && (p2 != noData)) {

                    // Calculate the elevation difference between the centerpoint and the points p1 and p2
                    z1 = p1 - z;
                    z2 = p2 - z;

                    // Calculate the coordinates of the normal to the triangular facet
                    nx = (yd[i] * z2 - yd[ii] * z1) * gridRes;
                    ny = (xd[ii] * z1 - xd[i] * z2) * gridRes;
                    nz = (xd[i] * yd[ii] - xd[ii] * yd[i]) * Math.pow(gridRes, 2);

                    // Calculate the downslope direction of the triangular facet
                    if (nx == 0) {
                        if (ny >= 0) {
                            hr = 0;
                        } else {
                            hr = pi;
                        }
                    } else {
                        if (nx >= 0) {
                            hr = pi / 2 - Math.atan(ny / nx);
                        } else {
                            hr = 3 * pi / 2 - Math.atan(ny / nx);
                        }
                    }

                    // Calculate the slope of the triangular facet
                    hs = -Math.tan(Math.acos(nz / (Math.sqrt(Math.pow(nx, 2) + Math.pow(ny, 2) + Math.pow(nz, 2)))));

                    // If the downslope direction is outside the triangular facet, then use the direction of p1 or p2
                    if ((hr < (i) * pi / 4) || (hr > (i + 1) * pi / 4)) {
                        if (p1 < p2) {
                            hr = i * pi / 4;
                            hs = (z - p1) / (dd[i] * gridRes);
                        } else {
                            hr = ii * pi / 4;
                            hs = (z - p2) / (dd[ii] * gridRes);
                        }
                    }

                    rFacet[c] = hr;
                    sFacet[c] = hs;

                } else {
                    if ((p1 != noData) && (p1 < z)) {
                        hr = ((float) i) / 4 * pi;
                        hs = (z - p1) / (dd[ii] * gridRes);
                        
                        rFacet[c] = hr;
                        sFacet[c] = hs;
                    }
                }
            }

            // Compute the total area of the triangular facets where water is flowing to
            for (c = 0; c < 8; c++){
                i = c;
                ii = (i + 1) % 8;

                if (sFacet[i] > 0) {                                                      // If the slope is downhill
                    if ((rFacet[i] > (i * pi / 4)) && (rFacet[i] < ((i + 1) * pi / 4))) {     // If the downslope direction is inside the 45 degrees of the triangular facet
                        valley[i] = sFacet[i];
                    } else if (rFacet[i] == rFacet[ii]) {                                    // If two adjacent triangular facets have the same downslope direction
                        valley[i] = sFacet[i];
                    } else if ((sFacet[ii] == noData) && (rFacet[i] == ((i + 1) * pi / 4))) {      // If the downslope direction is on the border of the current triangular facet, and the corresponding neigbour// s downslope is NoData
                        valley[i] = sFacet[i];
                    } else {
                        ii = (i + 7) % 8;
                        if ((sFacet[ii] == noData) && (rFacet[i] == (i * pi / 4))) {          // If the downslope direction is on the other border of the current triangular facet, and the corresponding neigbour// s downslope is NoData
                            valley[i] = sFacet[i];
                        }
                    }
                }

                valleySum = valleySum + Math.pow(valley[i], hExp);
                if (valleyMax < valley[i]) {
                    iMax = i;
                    valleyMax = valley[i];
                }
            }

            // Compute the proportional contribution for each of the triangular facets
            if (valleySum > 0) {
                if (hExp < 10) {
                    for (i = 0; i < 8; i++) {
                        valley[i] = (Math.pow(valley[i], hExp)) / valleySum;
                        portion[i] = 0;
                    }
                } else {
                    for (i = 0; i < 8; i++) {
                        if (i != iMax) {
                            valley[i] = 0;
                        } else {
                            valley[i] = 1;
                        }
                        portion[i] = 0;
                    }
                }

                if (rFacet[7] == 0) {
                    rFacet[7] = 2 * pi;
                }

                // Compute the contribution to each of the neighbouring gridcells
                for (c = 0; c < 8; c++){
                    i = c;
                    ii = (i + 1) % 8;

                    if (valley[i] > 0) {
                        portion[i] = portion[i] + valley[i] * ((i + 1) * pi / 4 - rFacet[i]) / (pi / 4);
                        portion[ii] = portion[ii] + valley[i] * (rFacet[i] - (i) * pi / 4) / (pi / 4);
                    }
                }

                // Apply the flow accumulation to the neighbouring gridcell (toStreamRow, toStreamCol)
                for (c = 0; c < 8; c++){
                    if (portion[c] > 0) {
                        a = fromCol + xd[c];
                        b = fromRow + yd[c];

                        if (a == toStreamCol & b == toStreamRow) {
                            side = FindSide(a, b, flowDirection, noData);

                            flowAccumTotal.incrementValue(b, a, flowAccumVal * portion[c]);

                            if (side == SIDE_MDInf.Side.RIGHT) {
                                flowAccumRight.incrementValue(b, a, flowAccumVal * portion[c]);
                            } else if (side == SIDE_MDInf.Side.LEFT) {
                                flowAccumLeft.incrementValue(b, a, flowAccumVal * portion[c]);
                            } else if (side == SIDE_MDInf.Side.UNKNOWN) {
                                flowAccumRight.incrementValue(b, a, (flowAccumVal * portion[c]) / 2);
                                flowAccumLeft.incrementValue(b, a, (flowAccumVal * portion[c]) / 2);
                            }
                        }
                    }
                }
            }
        }
    }

    private Side FindSide(int toStreamX, int toStreamY, int flowDirection, double noData) {
        
        // Variables for cell (x, y)
        double[] flowVec = new double[3];

        // Variables for the cell where cell (x, y) is flowing to (=> (toStreamX, toStreamY))
        int stream1_X;
        int stream1_Y;
        int stream1_Dir;
        double[] stream1Vec = new double[3];

        // Variables for the upstream cell(s) of cell stream1
        int stream2_X;
        int stream2_Y;
        int stream2_Dir;
        double[] stream2Vec = new double[3];

        double sp;
        double zcpA;
        double zcpB;
        double zcpC;

        // Initialize the stream side variables to their default. 
        // Default: The side of the flow line is not determined.
        boolean left = true;
        boolean right = true;

        // Determine the coordinates and stream direction of the adjacent grid cell, to which the flow line points.
        stream1_X = toStreamX;
        stream1_Y = toStreamY;
        stream1_Dir = D8FlowDirection(stream1_X, stream1_Y, noData);

        if (stream1_Dir == -1) {
            return Side.UNKNOWN;
        }
        
        // Write the direction of the flow line as vector:
        flowVec[0] = Get_xTo(flowDirection, 0);
        flowVec[1] = Get_yTo(flowDirection, 0);
        flowVec[2] = 0.0;     // z-component is normally 0

        // Write the streamflow directon as vector:
        stream1Vec[0] = Get_xTo(stream1_Dir, 0);
        stream1Vec[1] = Get_yTo(stream1_Dir, 0);
        stream1Vec[2] = 0.0;    // z-component is normally 0

        // Initialize the upstream streamflow direction vector and set all components to zero 
        stream2Vec[0] = 0.0;
        stream2Vec[1] = 0.0;
        stream2Vec[2] = 0.0;

        // Calculate the scalar product
        sp = flowVec[0] * stream1Vec[0] + flowVec[1] * stream1Vec[1];

        // Adjust the scalar product by dividing it by the lengths of FL_Vec and stream1Vec
        sp = sp / Math.sqrt(flowVec[0] * flowVec[0] + flowVec[1] * flowVec[1]) / Math.sqrt(stream1Vec[0] * stream1Vec[0] + stream1Vec[1] * stream1Vec[1]);

        if (Math.abs(sp - (-1)) < 0.00001) {
            // SP is (approximately) equal to -1! 
            // The flow line is hence oriented opposite to the streamflow direction.
            // Further calculations are skipped: The side of the flow line remains the default.
            // This can occur if an endpoint of the streamflow direction map does *not*
            // point to a missing value. In other words, the stream outlet lies // inside// the 
            // DEM and not right on the border of the DEM.
            // Since this can be intentional, a user notification is only optional.

            // *** Notification of the user (optional)***
        } else {
            int nTributaries;
            boolean prevRight;
            boolean isUpstream;

            // Initialize the number of tributaries and other auxiliary variables
            nTributaries = 0;    // default: channel head
            prevRight = true;
            isUpstream = false;

            // The full vector-cross-product of the streamflow direction and the flow line direction
            // CP_A = FL_Vec * stream1Vec;
            // is not calculated because it is more efficient to calculate only the z-component 
            // of the cross-product:

            zcpA = flowVec[0] * stream1Vec[1] - flowVec[1] * stream1Vec[0];

            // Look for upstream tributaries / stream grid cells
            for (int i = 0; i < 8; i++) {
                // find adjacent grid cell coordinates
                stream2_X = Get_xTo(i, stream1_X);
                stream2_Y = Get_yTo(i, stream1_Y);

                // Make sure it is a stream cell
                if (streams.getValue(stream2_Y, stream2_X) > 0) {

                    // Is the stream cell an upstream tributary?
                    stream2_Dir = D8FlowDirection(stream2_X, stream2_Y, noData);
                    if (stream2_Dir != -1 && stream1_X == Get_xTo(stream2_Dir, stream2_X) && stream1_Y == Get_yTo(stream2_Dir, stream2_Y)) {
                        isUpstream = true;
                    } else {
                        isUpstream = false;
                    }

                    if (isUpstream == true) {
                        // The stream cell is an upstream tributary!
                        nTributaries += 1;

                        // Convert the upstream streamflow direction to a vector
                        stream2Vec[0] = Get_xTo(stream2_Dir, 0);
                        stream2Vec[1] = Get_yTo(stream2_Dir, 0);
                        stream2Vec[2] = 0.0;

                        // Calculate only z-component of the vector-cross-product
                        zcpB = flowVec[0] * stream2Vec[1] - flowVec[1] * stream2Vec[0];

                        // store the previous position of the flow line
                        prevRight = right;

                        // Test if Z components have the same sign
                        if (zcpA * zcpB > 0) {

                            // zcpA and zcpB have the same sign, thus the position of the flow line is the same for both stream grid cells
                            right = (zcpB > 0);
                            left = ! right;
                        } else {
                            // Since zcpA and zcpB have opposite signs (or zcpA and zcpB are both zero), the flow line is located at a sharp bend (or parallel to a straight part of the stream). 

                            // Calculate only z-component of the vector-cross-product
                            zcpC = stream1Vec[0] * stream2Vec[1] - stream1Vec[1] * stream2Vec[0];

                            right = (zcpC > 0);
                            left = ! right;
                        }

                        if ((nTributaries > 1) & (right != prevRight)) {

                            // It is a junction (nTributaries > 1) and the flow line lies between two tributaries
                            left = right = true;

                            // Set i to 9 in order to exit the loop (there is no use in checking for more potential tributaries since the flow line will always lie between two tributaries)
                            i = 9;
                        }
                    }
                }
            }
        }

        if (right == true & left == true) {
            return Side.UNKNOWN;
        } else if (right == true) {
            return Side.RIGHT;
        } else if (left == true) {
            return Side.LEFT;
        } else {
            return Side.UNKNOWN;
        }
    }

    private int Get_xTo(int direction, int x) {
        direction = direction % 8;

        if (direction < 0) {
            direction += 8;
        }

        return (x + xd[direction]);
    }

    private int Get_yTo(int direction, int y) {
        direction = direction % 8;

        if (direction < 0) {
            direction += 8;
        }

        return (y + yd[direction]);
    }

    private int D8FlowDirection(int col, int row, double noData) {
        double slope;
        double maxSlope = Double.NEGATIVE_INFINITY;
        int flowDir = -1;
        int a, b;
        double zFrom;
        double zTo;

        // Find the neighbour with the steepest slope
        zFrom = dem.getValue(row, col);

        for (int c = 0; c < 8; c++){
            a = col + xd[c];
            b = row + yd[c];
            zTo = dem.getValue(b, a);
            if ((zFrom > zTo) && (zTo != noData)) {
                slope = (zFrom - zTo) / dd[c];
                if (slope > maxSlope) {
                    maxSlope = slope;
                    flowDir = c;
                }
            }
        }

        return flowDir;
    }
}
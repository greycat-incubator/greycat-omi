/**
 * Copyright 2017 The GreyCat Authors.  All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package omi;

import greycat.Graph;
import omi.messages.Messages;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * An abstract class to define an ODF handler
 */
public abstract class ODFHandler {

    private Graph _graph;

    /**
     * Get the greycat grapph
     *
     * @return Greycat graph
     */
    public Graph getGraph() {
        return _graph;
    }

    /**
     * Set the graph that can be used in parse method
     *
     * @param graph Greycat grqph
     */
    void setGraph(Graph graph) {
        this._graph = graph;
    }

    public String buildHierarchy(String[] ids, String infoItem) {
        return buildHierarchy(ids, null, infoItem);
    }

    /**
     * Build the READ message for a given path
     *
     * @param path Path to follow
     * @return READ message
     */
    public String readMessage(String path, String infoItem) {
        return Messages.envelope("<omi:read msgformat=\"odf\"><omi:msg><Objects xmlns=\"odf.xsd\">" + buildHierarchy(path.split("/"), infoItem) + "</Objects></omi:msg></omi:read>", 0);
    }

    /**
     * Build the READ message for a given path in a timeframe
     *
     * @param path  Path to follow
     * @param begin Begin date as formatted string
     * @param end   End date as formatted string
     * @return READ message
     */
    public String readMessage(String path, String begin, String end, String infoItem) {
        return Messages.envelope("<omi:read msgformat=\"odf\"  end=\"" + end + "\" begin=\"" + begin + "\"><omi:msg><Objects xmlns=\"odf.xsd\">" + buildHierarchy(path.split("/"), infoItem) + "</Objects></omi:msg></omi:read>", 0);
    }

    public String readAmountMessage(String path, int amount, String take, String infoItem) {
        switch (take) {
            case OMIConstants.NEWEST:
                return Messages.envelope("<omi:read msgformat=\"odf\" newest=\"" + amount + "\"><omi:msg><Objects xmlns=\"odf.xsd\">" + buildHierarchy(path.split("/"), infoItem) + "</Objects></omi:msg></omi:read>", 0);
            case OMIConstants.OLDEST:
                return Messages.envelope("<omi:read msgformat=\"odf\" oldest=\"" + amount + "\"><omi:msg><Objects xmlns=\"odf.xsd\">" + buildHierarchy(path.split("/"), infoItem) + "</Objects></omi:msg></omi:read>", 0);
            default:
                throw new RuntimeException("Only " + OMIConstants.NEWEST + " and " + OMIConstants.OLDEST + " are supported by the O-MI/O-DF specification");
        }
    }

    /**
     * Format a date upon a custom format
     *
     * @param date   Date to format
     * @param format Custom format
     * @return Date formatted
     */
    public String parseDate(Date date, String format) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(format);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(date);
    }

    /**
     * Build the WRITE message for a given path and value
     *
     * @param path  Path to follow
     * @param value Value to write
     * @param infoItem InfoItem name
     * @return WRITE message
     */
    public String writeMessage(String path, Object value, String infoItem) {
        return Messages.envelope("<omi:write msgformat=\"odf\"><omi:msg><Objects xmlns=\"odf.xsd\">" + buildHierarchy(path.split("/"), value, infoItem) + "</Objects></omi:msg></omi:write>", 0);
    }

    /**
     * Parse the incoming ODF message
     *
     * @param response  Text-based message
     * @param sourceUrl Source server
     */
    public abstract void parse(String response, String sourceUrl);

    /**
     * Convert a value to an ODF tag
     * @param value Value to convert
     * @param infoItem infoItem name
     * @return A valid ODF tag
     */
    public abstract String valueToODF(Object value, String infoItem);

    /**
     * Build the ODF hierarchy for an ordered list of ids and a value
     *
     * @param ids   Ordered list of ids
     * @param value Value
     * @param infoItem infoItem name
     * @return ODF hierarchy
     */
    public abstract String buildHierarchy(String[] ids, Object value, String infoItem);

    /**
     * Define the date format handled by the ODF/OMI server
     *
     * @return A date format (must be compliant to Java Dateformat)
     */
    public abstract String getDateFormat();

}

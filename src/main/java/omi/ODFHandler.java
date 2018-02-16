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

/**
 * An abstract class to define a project specific ODF handler
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

    public String buildHierarchy(String[] ids) {
        return buildHierarchy(ids, "");
    }

    /**
     * Build the READ message for a given path
     *
     * @param path Path to follow
     * @return READ message
     */
    public String readMessage(String path) {
        return Messages.envelope("<omi:read msgformat=\"odf\"><omi:msg><Objects xmlns=\"odf.xsd\">" + buildHierarchy(path.split("/")) + "</Objects></omi:msg></omi:read>", 0);
    }

    /**
     * Build the WRITE message for a given path and value
     *
     * @param path  Path to follow
     * @param value Value to write
     * @return WRITE message
     */
    public String writeMessage(String path, Object value) {
        return Messages.envelope("<omi:write msgformat=\"odf\"><omi:msg><Objects xmlns=\"odf.xsd\">" + buildHierarchy(path.split("/"), value) + "</Objects></omi:msg></omi:write>", 0);
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
     * @return A valid ODF tag
     */
    public abstract String valueToODF(Object value);

    /**
     * Build the ODF hierarchy for an ordered list of ids and a value
     *
     * @param ids   Ordered list of ids
     * @param value Value
     * @return ODF hierarchy
     */
    public abstract String buildHierarchy(String[] ids, Object value);
}

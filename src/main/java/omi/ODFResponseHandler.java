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
 * An abstract class to define a response handler
 */
public abstract class ODFResponseHandler {

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

    /**
     * Parse the incoming ODF message
     *
     * @param response  Text-based message
     * @param sourceUrl Source server
     */
    public abstract void parse(String response, String sourceUrl);

    public abstract String buildHierarchy(String[] ids);

    public String readPath(String path) {
        return Messages.envelope("<omi:read msgformat=\"odf\"><omi:msg><Objects xmlns=\"odf.xsd\">" + buildHierarchy(path.split("/")) + "</Objects></omi:msg></omi:read>", 0);
    }
}

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

import greycat.Constants;
import greycat.Graph;
import greycat.Node;
import greycat.Type;
import greycat.plugin.Plugin;

import java.util.HashMap;

import static greycat.Tasks.newTask;
import static greycat.Tasks.thenDo;


/**
 * OMI Plugin
 */
public class OMIPlugin implements Plugin {

    private final String _gatewayId;
    private boolean _liveUpdate;
    private HashMap<String, OMIScheduler> _schedulers = new HashMap<>();
    private ODFHandler _responseHandler;

    /**
     * Build the OMI plugin
     *
     * @param gatewayId          Name of the gateway
     * @param odfHandler Response handler to OMI requests
     * @param liveUpdate         Live update
     */
    public OMIPlugin(String gatewayId, ODFHandler odfHandler, boolean liveUpdate) {
        _gatewayId = gatewayId;
        _responseHandler = odfHandler;
        _liveUpdate = liveUpdate;
    }

    public OMIPlugin(String gatewayId, ODFHandler odfHandler) {
        this(gatewayId, odfHandler, true);
    }

    /**
     * Start the OMI plugin
     *
     * @param graph Greycat graph
     */
    @Override
    public void start(Graph graph) {
        _responseHandler.setGraph(graph);

        graph.addConnectHook(result -> {
            newTask()
                    .declareIndex("gateway", "protocol")
                    .readIndex("gateway", "omi")
                    .ifThen(ctx -> ctx.resultAsNodes().size() == 0, newTask().travelInTime(Constants.BEGINNING_OF_TIME_STR).createNode().setAttribute("protocol", Type.STRING, "omi").declareLocalIndex("root", "url").updateIndex("gateway"))
                    .traverse("root")
                    .forEach(
                            newTask()
                                    .log("OMI Root node identified {{url}}")
                                    .thenDo(ctx -> {
                                        String url = ctx.resultAsNodes().get(0).get("url").toString();
                                        ctx.defineVariable("urlRoot", url);
                                        if (_liveUpdate) {
                                            _schedulers.put(url, new OMIScheduler(graph, url, _responseHandler));
                                            ctx.continueTask();
                                        } else {
                                            System.err.println("[OMI] Live update is deactivated");
                                            ctx.endTask(ctx.result(), ctx.result().exception());
                                        }
                                    })
                                    .traverse("resources")
                                    .forEach(
                                            thenDo(ctx -> {
                                                Node node = ctx.resultAsNodes().get(0);
                                                _schedulers.get(ctx.variable("urlRoot").get(0).toString()).add(node.id());
                                                ctx.continueTask();
                                            })
                                    )
                    ).execute(graph, cb -> {
            });
            result.on(true);
        });
    }

    /**
     * Stop the plugin
     * All the schedulers are stopped when this method is triggered
     */
    @Override
    public void stop() {
        _schedulers.values().forEach(OMIScheduler::stop);
    }
}
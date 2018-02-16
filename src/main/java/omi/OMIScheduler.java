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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static greycat.Tasks.newTask;

/**
 * A scheduler is responsible to orchestrate the OMI requests for a given server
 */
public class OMIScheduler {

    private ExecutorService _scheduler = Executors.newScheduledThreadPool(10);
    private OMIConnector _connector;
    private String _server;
    private Graph _graph;

    /**
     * Default constructor
     *
     * @param server          OMI server URL (eg. wss://remote_server/)
     * @param responseHandler Response handler
     */
    public OMIScheduler(Graph graph, String server, ODFHandler responseHandler) {
        _graph = graph;
        _server = server;
        _connector = new OMIConnector(server, 10240, 600000, responseHandler);
    }

    /**
     * Add a greycat node to the scheduler
     * The node must have the following attributes: 'id', 'path', 'period', 'action'
     *
     * @param greycatId Greycat id
     */
    public void add(long greycatId) {
        newTask().lookup(String.valueOf(greycatId)).thenDo(ctx -> {
            String id = (String) ctx.resultAsNodes().get(0).get("id");
            String path = (String) ctx.resultAsNodes().get(0).get(OMIConstants.PATH);
            String action = (String) ctx.resultAsNodes().get(0).get(OMIConstants.ACTION);
            switch (action) {
                case OMIConstants.READ:
                    System.out.println("Scheduler[" + _server + "]+= " + id);
                    int period = (int) ctx.resultAsNodes().get(0).get("period");
                    _scheduler.execute(buildThread(id, period, path));
                    break;
                case OMIConstants.WRITE:
                    ctx.resultAsNodes().get(0).listen(changeTimes -> {
                        newTask().lookup(String.valueOf(greycatId)).travelInTime(String.valueOf(changeTimes[0])).thenDo(ctx1 -> {
                            Object value = ctx1.resultAsNodes().get(0).get("value");
                            String message = _connector.getHandler().writeMessage(path, value);
                            _connector.send(message);
                            ctx1.continueTask();
                        }).execute(_graph, null);
                    });


                    break;
            }
            ctx.continueTask();
        }).execute(_graph, null);
    }

    /**
     * Get the websocket connector instanciated for the scheduler
     *
     * @return A websocket connector
     */
    public OMIConnector getConnector() {
        return _connector;
    }

    private Runnable buildThread(String id, int period, String path) {
        return () -> {
            try {
                while (true) {
                    System.out.println("Refresh for " + id);
                    _connector.send(_connector.getHandler().readMessage(path));
                    Thread.sleep(period);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
    }

    /**
     * Proper way to stop the scheduler (shutdown the threads and close the websocket connection)
     */
    public void stop() {
        _scheduler.shutdown();
        _connector.close();
    }
}

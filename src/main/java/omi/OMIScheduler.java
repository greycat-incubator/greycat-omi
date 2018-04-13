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

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static greycat.Tasks.newTask;
import static omi.OMIConstants.TS_LAST_VALUE_RECEIVED;

/**
 * A scheduler is responsible to orchestrate the OMI requests for a given server
 */
public class OMIScheduler {

    private ExecutorService _scheduler = Executors.newCachedThreadPool();
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
        _connector = new OMIConnector(server, 10240, 60 * 60 * 1000L, responseHandler);
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
                    long period = (long) ctx.resultAsNodes().get(0).get("period");
                    System.out.println("Scheduler[" + _server + "]+= " + id + "(Period: " + period + "ms)");
                    _scheduler.execute(buildThread(id, period, path, greycatId));
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

    private Runnable buildThread(String id, long period, String path, long greycatId) {
        return () -> {
            try {
                while (true) {
                    newTask()
                            .travelInTime(Constants.BEGINNING_OF_TIME_STR)
                            .lookup(String.valueOf(greycatId))
                            .setAsVar("node")
                            .attribute(OMIConstants.MODE)
                            .setAsVar("mode")
                            .ifThenElse(cond -> cond.variable("mode").get(0).equals(OMIConstants.NEWEST_UNTIL_NOW),
                                    newTask().readVar("node").traverse("raw")
                                            .timepoints("0", String.valueOf(System.currentTimeMillis()))
                                            .thenDo(ctx -> {
                                                        if (ctx.result().size() > 0) {
                                                            ctx.setVariable(TS_LAST_VALUE_RECEIVED, ctx.result().get(ctx.result().size() - 1));
                                                        } else {
                                                            ctx.setVariable(TS_LAST_VALUE_RECEIVED, 0L);
                                                        }
                                                        ctx.continueTask();
                                                    }
                                            )
                                            .readVar("node").thenDo(ctx -> {
                                        long lastUpdate = (long) ctx.variable(TS_LAST_VALUE_RECEIVED).get(0);
                                        ctx.setVariable("now", System.currentTimeMillis());
                                        String begin = _connector.getHandler().parseDate(new Date(lastUpdate), _connector.getHandler().getDateFormat());
                                        String end = _connector.getHandler().parseDate(new Date((Long) ctx.variable("now").get(0)), _connector.getHandler().getDateFormat());
                                        String request = _connector.getHandler().readMessage(path, begin, end);
                                        _connector.send(request);
                                        ctx.continueTask();
                                    }),
                                    newTask().ifThenElse(cond -> cond.variable("mode").get(0).equals(OMIConstants.NEWEST),
                                            newTask().thenDo(ctx -> {
                                                String request = _connector.getHandler().readAmountMessage(path, 50, OMIConstants.NEWEST);
                                                _connector.send(request);
                                                ctx.continueTask();
                                            }),
                                            newTask().ifThenElse(cond -> cond.variable("mode").get(0).equals(OMIConstants.OLDEST),
                                                    newTask().thenDo(ctx -> {
                                                        String request = _connector.getHandler().readAmountMessage(path, 50, OMIConstants.OLDEST);
                                                        _connector.send(request);
                                                        ctx.continueTask();
                                                    }),
                                                    newTask().thenDo(ctx -> {
                                                        throw new RuntimeException("Unknown read mode: " + ctx.variable("mode").get(0));
                                                    })
                                            )
                                    )
                            ).execute(_graph, null);
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

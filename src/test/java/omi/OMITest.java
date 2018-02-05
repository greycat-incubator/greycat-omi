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

import greycat.*;
import greycat.leveldb.LevelDBStorage;

import java.io.File;
import java.util.Arrays;

import static greycat.Tasks.newTask;

public class OMITest {

    public static void main(String[] args) {

        String OMI_ROOT_URL = "wss://localhost"; //Change for a valid OMI node (eg. wss://otaniemi3d.cs.hut.fi/omi/node/)
        String VALID_PATH_TO_RESOURCE = "K1/101/co2";
        String VALID_ID = "co2";

        String DB_TEST = "db_test" + System.currentTimeMillis();

        ODFResponseHandler handler = new ODFResponseHandler() {
            @Override
            public void parse(String response, String sourceUrl) {
                System.out.println("Received: " + response + " from " + sourceUrl);
            }

            @Override
            public String buildHierarchy(String[] ids) {
                if (ids.length == 0) return "<InfoItem name=\"sosa:hasSimpleResult\"/>";
                else
                    return "<Object><id>" + ids[0] + "</id>" + buildHierarchy(Arrays.copyOfRange(ids, 1, ids.length)) + "</Object>";
            }
        };

        Graph graph = new GraphBuilder()
                .withStorage(new LevelDBStorage(DB_TEST))
                .withPlugin(new OMIPlugin("omi", handler)).build();


        Task addTestNode = newTask().println("Create test node").createNode()
                .setAttribute("id", Type.STRING, VALID_ID)
                .setAttribute("path", Type.STRING, VALID_PATH_TO_RESOURCE)
                .setAttribute("period", Type.INT, "2000")
                .setAsVar("testNode")
                .readIndex("gateway", "omi")
                .traverse("root", OMI_ROOT_URL)
                .traverse("resources", VALID_ID)
                .ifThen(cond -> cond.resultAsNodes().size() == 0, newTask().print("\t {{id}}").readIndex("gateway", "omi")
                        .traverse("root", OMI_ROOT_URL).addVarTo("resources", "testNode").println("\t Completed..."));


        graph.connect(isConnected -> {
            newTask()
                    .travelInTime(Constants.BEGINNING_OF_TIME_STR)
                    .print("Setting the OMI root node")
                    .readIndex("gateway", "omi")
                    .setAsVar("theomi")
                    .traverse("root", OMI_ROOT_URL)
                    .ifThen(cond -> cond.resultAsNodes().size() == 0, newTask().createNode().setAttribute("url", Type.STRING, OMI_ROOT_URL).declareLocalIndex("resources", "id") // Server node
                            .setAsVar("theroot")
                            .readVar("theomi").addVarTo("root", "theroot"))
                    .execute(graph, cb -> {
                        TaskContext tc = addTestNode.prepare(graph, null, result -> {
                            graph.disconnect(disconnected -> {
                                graph.connect(reconnect -> {
                                    System.out.println("Graph restarted with OMI node");
                                });
                            });
                        });
                        addTestNode.executeUsing(tc);
                    });
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println(new File("./" + DB_TEST).delete())));

    }
}
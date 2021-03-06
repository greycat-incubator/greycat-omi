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

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketException;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import java.net.URI;
import java.util.Date;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The websocket transmitting the OMI request and ODF data structures
 */
@WebSocket(maxTextMessageSize = 1048576, maxBinaryMessageSize = 1048576)
public class OMIConnector {

    private SslContextFactory sslContextFactory = new SslContextFactory();
    private WebSocketClient client;
    private Session currentSession;
    private ODFHandler _handler;
    private String _url;
    private Boolean isConnected;

    int tries = 0;
    private int MAX_TRIES = 10;

    /**
     * Build the websocket
     *
     * @param url                Server url (eg. wss://omiserver/)
     * @param _maxMessageSize    max message size
     * @param _maxIdleTime       max idle time
     * @param odfHandler response handler
     */
    public OMIConnector(String url, int _maxMessageSize, long _maxIdleTime, ODFHandler odfHandler) {
        _handler = odfHandler;
        _url = url;
        sslContextFactory.setTrustAll(true);
        client = new WebSocketClient(sslContextFactory);

        try {
            client.getPolicy().setIdleTimeout(_maxIdleTime);
            client.setMaxTextMessageBufferSize(_maxMessageSize);
            client.start();

            reconnect();

            System.out.println("session = " + currentSession);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Send a message (upon the ODF format) through the websocket
     *
     * @param message ODF message
     */
    public void send(String message) {
        if (isConnected) {
            try {
                currentSession.getRemote().sendString(message, new WriteCallback() {
                    @Override
                    public void writeFailed(Throwable throwable) {
                        if (throwable.getMessage().equals("Blocking message pending 10000 for BLOCKING")) {
                            // Retry the sending
                            send(message);
                        } else {
                            throwable.printStackTrace();
                        }
                    }

                    @Override
                    public void writeSuccess() {
                        //Nothing
                    }
                });
            } catch (WebSocketException e) {
                if (!e.getMessage().contains("current state [CLOSED]")) {
                    e.printStackTrace(); // Drop closed exception as the reconnection is handled by onClose, code=1006
                }
            }
        }
    }

    /**
     * Properly close the websocket
     */
    public void close() {
        try {
            isConnected = false;
            client.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnWebSocketConnect
    public void onConnect(Session sess) {
        System.out.println("Websocket connected to " + sess.getRemote().getInetSocketAddress().toString());
        isConnected = true;
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        isConnected = false;
        System.err.println(new Date() + " - WS Closed. statusCode = [" + statusCode + "], reason = [" + reason + "]");
        switch (statusCode) {
            case 1006: // WebSocket Read EOF -> restart the websocket
                System.out.println("Reconnecting the websocket...");
                reconnect();
                break;
            case 1001:
                System.out.println("Websocket shutdown");
                break;
            default:
                System.err.println("Don't know what to do");
        }


    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        isConnected = false;
        System.err.println("Websocket received an error: " + cause.getMessage());
    }

    @OnWebSocketMessage
    public void onMessage(String msg) {
        Pattern p = Pattern.compile("returnCode=\"([0-9]{3})\"");
        Matcher m = p.matcher(msg);
        if (m.find()) {
            int code = Integer.parseInt(m.group(1));
            switch (code) {
                case 200:
                    _handler.parse(msg, _url);
                    break;
                case 404:
                    System.err.println("Path not found or no fresher values [msg=" + msg + "]");
                    break;
                case 400:
                    System.err.println("Bad request [msg=" + msg + "]");
                    break;
                case 500:
                    System.err.println("Server internal error [msg=" + msg + "]");
                    break;
            }
        } else {
            System.err.println("Received a non-valid ODF message [msg=" + msg + "]");
        }
    }

    private void reconnect() {

        try {
            client.start();
            Future<Session> fut = client.connect(this, URI.create(this._url));
            currentSession = fut.get();
            tries = 0;
            isConnected = true;
        } catch (Exception e) {
            isConnected = false;
            System.err.println("Connection error: " + e.getMessage());
            if (++tries < MAX_TRIES) {
                System.out.println("Reconnecting in " + tries * 10 + " seconds...");
                try {
                    Thread.sleep(tries * 10 * 1000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            } else {
                System.err.println("Cannot connect to O-MI node... Retry in 60min");
                try {
                    Thread.sleep(3600 * 1000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
            reconnect();
        }

    }

    public ODFHandler getHandler() {
        return _handler;
    }

}

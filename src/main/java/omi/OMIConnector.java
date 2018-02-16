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
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import java.net.URI;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The websocket transmitting the OMI request and ODF data structures
 */
@WebSocket
public class OMIConnector {

    private SslContextFactory sslContextFactory = new SslContextFactory();
    private WebSocketClient client;
    private Session currentSession;
    private ODFHandler _handler;
    private String _url;

    /**
     * Build the websocket
     *
     * @param url                Server url (eg. wss://omiserver/)
     * @param _maxMessageSize    max message size
     * @param _maxIdleTime       max idle time
     * @param odfHandler response handler
     */
    public OMIConnector(String url, int _maxMessageSize, int _maxIdleTime, ODFHandler odfHandler) {
        _handler = odfHandler;
        _url = url;
        sslContextFactory.setTrustAll(true);
        client = new WebSocketClient(sslContextFactory);

        try {
            client.start();
            client.setMaxTextMessageBufferSize(_maxMessageSize);
            client.setMaxIdleTimeout(_maxIdleTime);
            Future<Session> fut = client.connect(this, URI.create(url));
            currentSession = fut.get();
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
    }

    /**
     * Properly close the websocket
     */
    public void close() {
        try {
            client.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnWebSocketConnect
    public void onConnect(Session sess) {
        System.out.println("Websocket connected to " + sess.getRemote().getInetSocketAddress().toString());
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        System.out.println("WS Closed. statusCode = [" + statusCode + "], reason = [" + reason + "]");

    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        System.out.println("Websocket received an error");
        cause.printStackTrace();
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
                    System.err.println("Path not found [msg=" + msg + "]");
                    break;
                case 400:
                    System.err.println("Bad request [msg=" + msg + "]");
                    break;
                case 500:
                    System.err.println("Server internal error [msg=" + msg + "]");
                    break;
            }
        } else {
            System.err.println("Received a non-valid ODF message");
        }
    }


    public ODFHandler getHandler() {
        return _handler;
    }

}

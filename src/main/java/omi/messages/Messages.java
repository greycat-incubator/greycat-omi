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
package omi.messages;

public class Messages {

    public static String envelope(String content, int ttl) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<omi:omiEnvelope xmlns:xs=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:omi=\"omi.xsd\" version=\"1.0\" ttl=\"" + ttl + "\">" +
                content +
                "</omi:omiEnvelope>";
    }

    public static String readAll(int ttl) {
        return envelope("<omi:read msgformat=\"odf\">" +
                "    <omi:msg>" +
                "      <Objects xmlns=\"odf.xsd\"/>" +
                "    </omi:msg>" +
                "  </omi:read>", ttl);
    }

    public static String readAll() {
        return readAll(0);
    }

}

/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.microprofile.tyrus;

import javax.enterprise.context.Dependent;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import java.util.logging.Logger;

/**
 * Class ServerConfigurator.
 */
@Dependent
public class ServerConfigurator extends ServerEndpointConfig.Configurator {
    private static final Logger LOGGER = Logger.getLogger(EchoEndpoint.class.getName());

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        LOGGER.info("ServerConfigurator called during handshake");
        super.modifyHandshake(sec, request, response);
        EchoEndpoint.modifyHandshakeCalled.set(true);
    }
}
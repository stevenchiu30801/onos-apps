/*
 * Copyright 2018-present Open Networking Foundation
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
package nctu.winlab.dhcpfwd;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.config.Config;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 *  Configuration object for DHCP config in EAPOL
 */
public class DhcpConfig extends Config<ApplicationId> {

    public static final String SERVERS = "dhcpServers";
    public static final String CONNECT_POINT = "connectPoint";
    public static final String NAME = "name";

    public Set<DhcpServerConfig> dhcpServers() {
        Set<DhcpServerConfig> servers = Sets.newHashSet();

        JsonNode serversNode = object.get(SERVERS);

        if(serversNode == null) {
            return servers;
        }

        serversNode.forEach(jsonNode -> {
            Optional<String> name;
            if(jsonNode.get(NAME) == null) {
                name = Optional.empty();
            }
            else {
                name = Optional.of(jsonNode.get(NAME).asText());
            }

            servers.add(new DhcpServerConfig(name,
                        ConnectPoint.deviceConnectPoint(jsonNode.path(CONNECT_POINT).asText())));
        });

        return servers;
    }

    public DhcpServerConfig getServerWithName(String name) {
        for(DhcpConfig.DhcpServerConfig server : dhcpServers()) {
            if(server.name().filter(name::equals).isPresent()) {
                return server;
            }
        }

        return null;
    }

    public static class DhcpServerConfig {
        private Optional<String> name;
        private ConnectPoint connectPoint;

        public DhcpServerConfig(Optional<String> name,
                                ConnectPoint connectPoint) {
            this.name = checkNotNull(name);
            this.connectPoint = checkNotNull(connectPoint);
        }

        public Optional<String> name() {
            return name;
        }

        public ConnectPoint connectPoint() {
            return connectPoint;
        }
    }
}

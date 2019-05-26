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
package nctu.winlab.vlansr;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.VlanId;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.DeviceId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 *  Configuration object for VLAN-based Segment Routing
 */
public class VlanSrConfig extends Config<ApplicationId> {

    public static final String DEVICES = "devices";
    public static final String DPID = "dpid";
    public static final String SID = "sid";
    public static final String ISEDGESWITCH = "isEdgeSwitch";
    public static final String SUBNET = "subnet";

    public Set<VlanSr> getVlanSrConfig() {
        Set<VlanSr> config = Sets.newHashSet();

        JsonNode configNode = object.get(DEVICES);

        if (configNode == null) {
            return config;
        }

        configNode.forEach(jsonNode -> {
            DeviceId deviceId;
            VlanId sid;
            boolean isEdgeSwitch;
            Optional<IpPrefix> subnet;

            deviceId = DeviceId.deviceId(jsonNode.path(DPID).asText());
            sid = VlanId.vlanId((short) jsonNode.path(SID).asInt());
            isEdgeSwitch = jsonNode.path(ISEDGESWITCH).asBoolean();
            if (isEdgeSwitch == true) {
                subnet = Optional.of(IpPrefix.valueOf(jsonNode.get(SUBNET).asText()));
            }
            else {
                subnet = Optional.empty();
            }

            config.add(new VlanSr(deviceId, sid, isEdgeSwitch, subnet));
        });

        return config;
    }

    public static class VlanSr {
        private DeviceId deviceId;
        private VlanId sid;
        private boolean isEdgeSwitch;
        private Optional<IpPrefix> subnet;

        public VlanSr(DeviceId deviceId,
                            VlanId sid,
                            boolean isEdgeSwitch,
                            Optional<IpPrefix> subnet) {
            this.deviceId = checkNotNull(deviceId);
            this.sid = checkNotNull(sid);
            this.isEdgeSwitch = checkNotNull(isEdgeSwitch);
            this.subnet = checkNotNull(subnet);
        }

        public DeviceId deviceId() {
            return deviceId;
        }

        public VlanId sid() {
            return sid;
        }

        public boolean isEdgeSwitch() {
            return isEdgeSwitch;
        }

        public Optional<IpPrefix> subnet() {
            return subnet;
        }
    }
}

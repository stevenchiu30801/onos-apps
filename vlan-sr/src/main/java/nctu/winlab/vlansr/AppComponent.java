/*
 * Copyright 2019-present Open Networking Foundation
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

import com.google.common.collect.ImmutableSet;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private static final int DEFAULT_TIMEOUT = 60;
    private static final int DEFAULT_PRIORITY = 10;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry configService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    private final InternalNetworkConfigListener configListener
            = new InternalNetworkConfigListener();

    private final Set<ConfigFactory> factories = ImmutableSet.of(
            new ConfigFactory<ApplicationId, VlanSrConfig>(APP_SUBJECT_FACTORY,
                                                           VlanSrConfig.class,
                                                           "vlan-sr") {
                @Override
                public VlanSrConfig createConfig() {
                    return new VlanSrConfig();
                }
            }
    );

    private ApplicationId appId;

    private int flowTimeout = DEFAULT_TIMEOUT;
    private int flowPriority = DEFAULT_PRIORITY;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.vlan-sr");

        configService.addListener(configListener);
        factories.forEach(configService::registerConfigFactory);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        configService.removeListener(configListener);
        factories.forEach(configService::unregisterConfigFactory);
        flowRuleService.removeFlowRulesById(appId);

        log.info("Stopped");
    }

    private void printConfiguration(VlanSrConfig vlanSrConfig) {
        if (vlanSrConfig == null) {
            log.info("No VLAN SR config available");
            return;
        }

        // Print VLAN SR configuration on log
        vlanSrConfig.getVlanSrConfig().forEach(vlanSr -> {
            log.info("deviceId: {}", vlanSr.deviceId().toString());
            log.info("sid: {}", vlanSr.sid().toShort());
            log.info("isEdgeSwitch: {}", vlanSr.isEdgeSwitch());
            if (vlanSr.subnet().isPresent()) {
                log.info("subnet: {}", vlanSr.subnet().get().toString());
            }
        });
    }

    private void installForwardSidRule(DeviceId deviceId, PortNumber portNumber, VlanId sid) {
        log.info("Install forward rule sid: {}, port: {} on device: {}", sid.toShort(), portNumber.toString(), deviceId.toString());

        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchVlanId(sid);

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(portNumber)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(flowTimeout)
                .add();

        flowObjectiveService.forward(deviceId, forwardingObjective);
    }

    private void installPushSidRule(DeviceId deviceId, PortNumber portNumber, VlanId sid, IpPrefix subnet) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchVlanId(VlanId.NONE)
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(subnet);

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .pushVlan()
                .setVlanId(sid)
                .setOutput(portNumber)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(flowTimeout)
                .add();

        flowObjectiveService.forward(deviceId, forwardingObjective);

    }

    private void installPopSidRule(DeviceId deviceId, PortNumber portNumber, VlanId sid, MacAddress dstMac) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchVlanId(sid)
                .matchEthDst(dstMac);

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .popVlan()
                .setOutput(portNumber)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(flowTimeout)
                .add();

        flowObjectiveService.forward(deviceId, forwardingObjective);

    }

    private void installMacForwardRule(DeviceId deviceId, PortNumber portNumber, MacAddress dstMac) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthDst(dstMac);

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(portNumber)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(flowTimeout)
                .add();

        flowObjectiveService.forward(deviceId, forwardingObjective);

    }


    private void installRules(VlanSrConfig vlanSrConfig) {
        vlanSrConfig.getVlanSrConfig().forEach(vlanSr -> {
            deviceService.getAvailableDevices().forEach(device -> { 
                if (vlanSr.deviceId().equals(device.id())) {
                    Set<Host> hosts = hostService.getConnectedHosts(device.id());

                    for (Host host : hosts) {
                        installPopSidRule(device.id(), host.location().port(), vlanSr.sid(), host.mac());
                        installMacForwardRule(device.id(), host.location().port(), host.mac());
                    }
                }
                else {
                    Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),
                                                               device.id(),
                                                               vlanSr.deviceId());

                    if (paths.isEmpty()) {
                        return;
                    }

                    for (Path path : paths) {
                        if (vlanSr.isEdgeSwitch() == true) {
                            installPushSidRule(device.id(), path.src().port(), vlanSr.sid(), vlanSr.subnet().get());
                        }

                        installForwardSidRule(device.id(), path.src().port(), vlanSr.sid());

                        return;
                    }
                }
            });
        });
    }

    private class InternalNetworkConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            switch (event.type()) {
                case CONFIG_ADDED:
                case CONFIG_UPDATED:
                case CONFIG_REMOVED:
                    if(event.configClass() == VlanSrConfig.class) {
                        VlanSrConfig vlanSrConfig = configService.getConfig(appId, VlanSrConfig.class);

                        // printConfiguration(vlanSrConfig);
                        installRules(vlanSrConfig);
                    }
                    break;
                default:
                    break;
            }
        }
    }
}

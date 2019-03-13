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
package nctu.winlab.learningbridge;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    private static final int DEFAULT_PRIORITY = 40000;
    private static final int DEFAULT_TIMEOUT = 10;

    private ReactivePacketProcessor processor = new ReactivePacketProcessor();

    private final TopologyListener topologyListener = new InternalTopologyListener();

    private HashMap<DeviceId, HashMap<MacAddress, PortNumber>> mapping = 
            new HashMap<>();

    private ApplicationId appId;
    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.learningbridge");

        packetService.addProcessor(processor, PacketProcessor.director(2));
        topologyService.addListener(topologyListener);

        requestIntercepts();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(processor);
        topologyService.removeListener(topologyListener);
        processor = null;

        log.info("Stopped");
    }

    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);

        // selector.matchEthType(Ethernet.TYPE_IPV6);
        // packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    private void learningMac(DeviceId id, MacAddress mac, PortNumber port) {
        HashMap<MacAddress, PortNumber> macToPort = mapping.get(id);

        if(macToPort == null) {
            macToPort = new HashMap<>();
            mapping.put(id, macToPort);
        }

        PortNumber p = macToPort.get(mac);

        if(p == null) {
            macToPort.put(mac, port);
            mapping.replace(id, macToPort);
        }
        else if(!p.equals(port)) {
            log.warn("Mapping of MAC {} on device {} changes. Original: {}, New: {}.", mac, id, p, port);
            macToPort.replace(mac, port);
            mapping.replace(id, macToPort);
        }
    }

    private void flood(PacketContext context) {
        if(topologyService.isBroadcastPoint(topologyService.currentTopology(),
                                            context.inPacket().receivedFrom())) {
            packetOut(context, PortNumber.FLOOD);
        }
        else {
            context.block();
        }

        packetOut(context, PortNumber.FLOOD);
    }

    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }

    private class InternalTopologyListener implements TopologyListener {
        @Override
        public void event(TopologyEvent event) {
            switch (event.type()) {
                default:
                    break;
            }
        }
    }

    private class ReactivePacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            DeviceId deviceId = pkt.receivedFrom().deviceId();
            PortNumber inPort = pkt.receivedFrom().port();
            MacAddress srcMac = pkt.parsed().getSourceMAC();

            short etherType = pkt.parsed().getEtherType();
            if(etherType == Ethernet.TYPE_LLDP || etherType == Ethernet.TYPE_BSN) {
                return;
            }

            learningMac(deviceId, srcMac, inPort);
        
            HashMap<MacAddress, PortNumber> macToPort = mapping.get(deviceId);
            MacAddress dstMac = pkt.parsed().getDestinationMAC();
            PortNumber outPort = macToPort.get(dstMac);

            if(outPort == null) {
                flood(context);
            }
            else {
                packetOut(context, outPort);

                TrafficSelector selectorBuilder = DefaultTrafficSelector.builder()
                        .matchEthDst(pkt.parsed().getDestinationMAC())
                        .build();

                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .setOutput(outPort)
                        .build();

                ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                        .withSelector(selectorBuilder)
                        .withTreatment(treatment)
                        .withPriority(DEFAULT_PRIORITY)
                        .withFlag(ForwardingObjective.Flag.VERSATILE)
                        .fromApp(appId)
                        .makeTemporary(DEFAULT_TIMEOUT)
                        .add();

                flowObjectiveService.forward(deviceId, forwardingObjective);
            }
        }
    }
}

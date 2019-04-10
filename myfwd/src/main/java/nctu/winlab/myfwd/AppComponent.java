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
package nctu.winlab.myfwd;

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
import org.onosproject.event.Event;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.DefaultTopologyVertex;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.topology.TopologyVertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Boolean;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;
import java.util.Queue;
import java.util.Set;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private static final int DEFAULT_TIMEOUT = 10;
    private static final int DEFAULT_PRIORITY = 10;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    private ProactivePacketProcessor processor = new ProactivePacketProcessor();

    private TopologyListener topologyListener = new InternalTopologyListener();

    private ApplicationId appId;

    private int count = 0;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.myfwd");

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

    private void installRule(PacketContext context, DeviceId deviceId, PortNumber portNumber) {
        log.info(String.format("Install flow rule on %s", deviceId.toString()));
        // Disable Packet-out since the pre-installed rules would be installed after the packet arrives the next device
        // This causes another Packet-in
        // //  Packet-out if DeviceID is the device receiving Packet-in
        // if (deviceId.equals(context.inPacket().receivedFrom().deviceId()))
        //     packetOut(context, portNumber);

        InboundPacket pkt = context.inPacket();

        TrafficSelector selectorBuilder = DefaultTrafficSelector.builder()
                .matchEthSrc(pkt.parsed().getSourceMAC())
                .matchEthDst(pkt.parsed().getDestinationMAC())
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(portNumber)
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

    private Path getPath(Topology topology, DeviceId src, DeviceId dst) {
        // TODO: Try to build topology graph and find a path using BFS
        // TopologyGraph topologyGraph = topologyService.getGraph(topology);
        // int deviceCount = topology.deviceCount();
        // HashMap<DeviceId, DeviceId> pred = new HashMap<>();
        // HashMap<DeviceId, Boolean> visited = new HashMap<>();
        //
        // Queue<DeviceId> queue = new LinkedList<>();

        Set<Path> paths = topologyService.getPaths(topology, src, dst);
        for (Path path : paths) {
            return path;
        }
        return null;
    }

    private class InternalTopologyListener implements TopologyListener {
        @Override
        public void event(TopologyEvent event) {
            log.info("Topology Event - {}", event);
            List<Event> reasons = event.reasons();
            if (reasons != null) {
                reasons.forEach(re -> {
                    if (re instanceof LinkEvent) {
                        LinkEvent le = (LinkEvent) re;
                        if (le.type() == LinkEvent.Type.LINK_ADDED)
                            log.info("Link added");
                        else if (le.type() == LinkEvent.Type.LINK_REMOVED)
                            log.info("Link removed");
                    }
                    else if (re instanceof DeviceEvent) {
                        DeviceEvent de = (DeviceEvent) re;
                        if (de.type() == DeviceEvent.Type.DEVICE_ADDED)
                            log.info("Device added");
                        else if (de.type() == DeviceEvent.Type.DEVICE_REMOVED)
                            log.info("Deivce removed");
                    }
                });
            }
        }
    }

    private class ProactivePacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            MacAddress macAddress = ethPkt.getSourceMAC();
            DeviceId deviceId = pkt.receivedFrom().deviceId();

            short etherType = pkt.parsed().getEtherType();
            if(etherType == Ethernet.TYPE_LLDP || etherType == Ethernet.TYPE_BSN) {
                return;
            }

            HostId id = HostId.hostId(ethPkt.getDestinationMAC());

            Host dst = hostService.getHost(id);
            if(dst == null) {
                flood(context);
                return;
            }

            // If the device receiving Packet-in is the dst device
            // There is no path between src and dst host in TopologyService.getPath
            if(deviceId.equals(dst.location().deviceId())) {
                if(!context.inPacket().receivedFrom().port().equals(dst.location().port())) {
                    log.info(String.format("Start to install path from %s to %s",
                                           HostId.hostId(ethPkt.getSourceMAC()).toString(),
                                           HostId.hostId(ethPkt.getDestinationMAC()).toString()));
                    installRule(context, deviceId, dst.location().port());
                }
            }

            // Path path = getPath(topologyService.getGraph(topologyService.currentTopology()),
            //         pkt.receivedFrom().deviceId(),
            //         dst.location().deviceId());
            Path path = getPath(topologyService.currentTopology(),
                    pkt.receivedFrom().deviceId(),
                    dst.location().deviceId());

            if (path == null) {
                return;
            }

            log.info(String.format("Start to install path from %s to %s",
                                   HostId.hostId(ethPkt.getSourceMAC()).toString(),
                                   HostId.hostId(ethPkt.getDestinationMAC()).toString()));
            // First install flow rule on the dst device
            // Since the path does not include the link between the dst device and the dst host
            installRule(context, dst.location().deviceId(), dst.location().port());
            // Install flow rules on each devices in path 
            List<Link> links = path.links();
            for (int i = links.size(); i > 0; i--) {
                Link link = links.get(i - 1);
                installRule(context, link.src().deviceId(), link.src().port());
            }
            // installRule(context, path.src().port());
        }
    }
}

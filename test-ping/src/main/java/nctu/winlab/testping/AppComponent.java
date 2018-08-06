/*
 * Copyright 2017-present Open Networking Laboratory
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
package nctu.winlab.testping;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.EthType;
import org.onlab.packet.IPv4;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.device.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    // @Reference annotation enables another class to reference the service
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    // @Reference annotation enables another class to reference the service
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    // @Reference annotation enables another class to reference the service
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    // @Reference annotation enables another class to reference the service
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    private final InternalDeviceListener deviceListener =
            new InternalDeviceListener();

    private static final int DEFAULT_PRIORITY = 40000;
    private ApplicationId appId;

    @Activate
    protected void activate() {
        log.info("Started");
        appId = coreService.registerApplication("nctu.winlab.testping");
        deviceService.addListener(deviceListener);

        defaultFlow();
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");

        flowRuleService.removeFlowRulesById(appId);
        deviceService.removeListener(deviceListener);
    }

    /**
     * Default ping flows for all devices.
     */
    protected void defaultFlow() {
        PortNumber out_port = PortNumber.NORMAL;
        Iterable<Device> device = deviceService.getAvailableDevices();

        TrafficSelector selectorBuilder = DefaultTrafficSelector.builder()
                .matchEthType(EthType.EtherType.IPV4.ethType().toShort())
                .matchIPProtocol(IPv4.PROTOCOL_ICMP)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(out_port)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder)
                .withTreatment(treatment)
                .withPriority(DEFAULT_PRIORITY)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makePermanent()
                .add();

        device.forEach(d -> flowObjectiveService.forward(d.id(),forwardingObjective));
    }

    /**
     * Listener for interface configuration events.
     */
    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            switch (event.type()) {
                case DEVICE_ADDED:
                case DEVICE_UPDATED:
                    defaultFlow();
                    break;
                default:
                    break;
            }
        }
    }
}

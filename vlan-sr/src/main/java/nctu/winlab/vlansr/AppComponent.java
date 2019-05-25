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
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.DeviceId;
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

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry configService;

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

        log.info("Stopped");
    }

    private void printConfiguration(VlanSrConfig vlanSrConfig) {
        if (vlanSrConfig == null) {
            log.info("No VLAN SR config available");
            return;
        }

        // Print VLAN SR configuration on log
        for (VlanSrConfig.VlanSr vlanSr : vlanSrConfig.getVlanSrConfig()) {
            log.info("deviceId: {}", vlanSr.deviceId().toString());
            log.info("sid: {}", vlanSr.sid().toShort());
            log.info("isEdgeSwitch: {}", vlanSr.isEdgeSwitch());
            if (vlanSr.subnet().isPresent())
                log.info("subnet: {}", vlanSr.subnet().get().toString());
        }
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

                        printConfiguration(vlanSrConfig);
                    }
                    break;
                default:
                    break;
            }
        }
    }
}

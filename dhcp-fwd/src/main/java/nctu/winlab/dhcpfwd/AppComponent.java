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
            new ConfigFactory<ApplicationId, DhcpConfig>(APP_SUBJECT_FACTORY,
                                                         DhcpConfig.class,
                                                         "dhcp") {
                @Override
                public DhcpConfig createConfig() {
                    return new DhcpConfig();
                }
            }
    ); 

    private ApplicationId appId;

    private DeviceId dhcpDeviceId;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.dhcpfwd");
        
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

    private void getDeviceConfiguration(DhcpConfig dhcpConfig) {
        if (dhcpConfig == null) {
            log.info("No DHCP config available");
            return;
        }

        Optional<DhcpConfig.DhcpServerConfig> dhcpServer = 
            dhcpConfig.dhcpServers().stream().findAny();

        if(!dhcpServer.isPresent()){
            log.error("DHCP server configuration is not found");
            return;
        }

        dhcpDeviceId = dhcpServer.get().connectPoint().deviceId();

        log.info("connectPoint deviceId: {}", dhcpDeviceId.toString());
    }

    private class InternalNetworkConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            switch (event.type()) {
                case CONFIG_ADDED:
                case CONFIG_UPDATED:
                case CONFIG_REMOVED:
                    if(event.configClass() == DhcpConfig.class) {
                        DhcpConfig dhcpConfig = configService.getConfig(appId, DhcpConfig.class);

                        getDeviceConfiguration(dhcpConfig);
                    }
                    break;
                default:
                    break;
            }
        }
    }
}

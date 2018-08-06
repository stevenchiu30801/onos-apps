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
 * diportLogibuted under the License is diportLogibuted on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.winlab.portstatistics;

import org.onosproject.net.Device;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.String;
import java.util.Iterator;
import java.util.List;
import java.util.TimerTask;

/**
 * TimerTask to retrieve port statistics
 */
public class PortStatsTask extends TimerTask {

    private Logger log;

    protected DeviceService deviceService;

    public void run() {
        Iterable<Device> devices = deviceService.getDevices();

        for(Device d : devices) {
            log.info("========= DeviceId " + d.id().toString() + " =========");
            log.info(String.format("%-8s%10s%10s%10s%10s", "port", "rcvBytes", "sntBytes", "deltaRcv", "deltaSnt"));

            List<Port> ports = deviceService.getPorts(d.id());
            for(Port p : ports) {
                PortStatistics portStats = deviceService.getStatisticsForPort(d.id(), p.number());
                PortStatistics portDeltaStats = deviceService.getDeltaStatisticsForPort(d.id(), p.number());

                String portLog = String.format("%-8s", p.number());

                if(portStats != null)
                    portLog += String.format("%10s%10s", portStats.bytesReceived(), portStats.bytesSent());
                else
                    portLog += String.format("%10s%10s", "n/a", "n/a");

                if(portDeltaStats != null)
                    portLog += String.format("%10s%10s", portDeltaStats.bytesReceived(), portDeltaStats.bytesSent());
                else
                    portLog += String.format("%10s%10s", "n/a", "n/a");

                log.info(portLog);
            }

            log.info("=================================================");
        }
    }

    public void setLog(Logger log) {
        this.log = log;
    }

    public void setDeviceService(DeviceService deviceService) {
        this.deviceService = deviceService;
    }
}

/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.floodlight.feature;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;

abstract class NoviflowSpecificFeature extends AbstractFeature {
    public static final String NOVIFLOW_MANUFACTURER_SUFFIX = "noviflow";

    boolean is100GbHw(IOFSwitch sw) {
        SwitchDescription description = sw.getSwitchDescription();
        if (description == null || description.getSoftwareDescription() == null
                || description.getHardwareDescription() == null) {
            return false;
        }

        if (E_SWITCH_MANUFACTURER_DESCRIPTION.equalsIgnoreCase(description.getManufacturerDescription())) {
            return false;
        }

        if (E_SWITCH_HARDWARE_DESCRIPTION_REGEX.matcher(description.getHardwareDescription()).matches()) {
            return false;
        }

        return description.getManufacturerDescription().toLowerCase().contains(NOVIFLOW_MANUFACTURER_SUFFIX);
    }
}

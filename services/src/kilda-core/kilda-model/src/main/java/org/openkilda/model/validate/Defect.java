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

package org.openkilda.model.validate;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;

@Getter
@EqualsAndHashCode
abstract class Defect<T extends Serializable, R extends Serializable> implements Serializable {
    private final R reference;
    private final T expected;
    private final T actual;

    public Defect(R reference, T expected, T actual) {
        this.reference = reference;
        this.expected = expected;
        this.actual = actual;

        if (expected == null && actual == null) {
            throw new IllegalArgumentException(
                    "Both expected and actual references are null, they can't represent defect");
        }
    }

    public boolean isMissing() {
        return actual == null;
    }

    public boolean isExcess() {
        return expected == null;
    }

    public boolean isMismatch() {
        return expected != null && actual != null;
    }
}

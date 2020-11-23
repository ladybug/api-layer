/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.caching.service.vsam;

import org.junit.jupiter.api.Test;
import org.zowe.apiml.caching.model.KeyValue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class VsamStorageTest {

    VsamStorage underTest = new VsamStorage(true);

    @Test
    void createCompositeKey() {
        assertThat(underTest.getCompositeKey("longservice", new KeyValue("klic", "value")),
            is("longklic"));
        assertThat(underTest.getCompositeKey("lo", new KeyValue("klic", "value")),
            is("lo  klic"));
        assertThat(underTest.getCompositeKey("lo", new KeyValue("k", "value")),
            is("lo  k   "));
        assertThat(underTest.getCompositeKey("Korben Dallas", new KeyValue("Multipass", "value")),
            is("KorbMult"));
    }
}
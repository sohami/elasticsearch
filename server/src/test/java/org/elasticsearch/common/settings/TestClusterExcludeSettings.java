/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.settings;

import org.elasticsearch.cluster.node.DiscoveryNodeFilters;
import org.elasticsearch.test.ESTestCase;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import static org.elasticsearch.cluster.node.DiscoveryNodeFilters.IP_VALIDATOR;
import static org.elasticsearch.cluster.node.DiscoveryNodeFilters.OpType.OR;

public class TestClusterExcludeSettings extends ESTestCase {

    public static final String CLUSTER_ROUTING_EXCLUDE_GROUP_PREFIX = "cluster.routing.allocation.exclude";

    public static final String FIRST_ATTRIBUTE = "attr_1";

    public static final String SECOND_ATTRIBUTE = "attr_2";

    private DiscoveryNodeFilters clusterExcludeFilters;

    public void testFilterDeciderSettingUpdater() throws Exception {
        final String first_attr_setting = CLUSTER_ROUTING_EXCLUDE_GROUP_PREFIX + "." + FIRST_ATTRIBUTE;
        final String second_attr_setting = CLUSTER_ROUTING_EXCLUDE_GROUP_PREFIX + "." + SECOND_ATTRIBUTE;

        final Setting.AffixSetting<String>CLUSTER_ROUTING_EXCLUDE_GROUP_SETTING =
                Setting.prefixKeySetting(CLUSTER_ROUTING_EXCLUDE_GROUP_PREFIX + ".", key ->
                        Setting.simpleString(key, value -> IP_VALIDATOR.accept(key, value), Setting.Property.Dynamic, Setting.Property.NodeScope));
        setClusterExcludeFilters(CLUSTER_ROUTING_EXCLUDE_GROUP_SETTING.getAsMap(Settings.EMPTY));
        AbstractScopedSettings service = new ClusterSettings(Settings.EMPTY,new HashSet<>(Arrays.asList(CLUSTER_ROUTING_EXCLUDE_GROUP_SETTING)));
        service.addAffixMapUpdateConsumer(CLUSTER_ROUTING_EXCLUDE_GROUP_SETTING, this::setClusterExcludeFilters, (a, b) -> {});

        assertNull(clusterExcludeFilters);

        // TEST 1: Update only 1 setting at a time
        // update the first setting in group
        String first_attr_setting_value = "1";
        service.applySettings(Settings.builder().put(first_attr_setting, first_attr_setting_value).build());
        Map<String, String[]> newFilter = clusterExcludeFilters.getFilters();
        assertEquals(first_attr_setting_value, newFilter.get(FIRST_ATTRIBUTE)[0]);
        assertNull(newFilter.get(SECOND_ATTRIBUTE));

        // TEST 2: update the second setting in group, this will only keep the second setting value whereas expectation is it should
        // keep both previous and new setting
        String second_attr_setting_value = "abc";
        service.applySettings(Settings.builder().put(second_attr_setting, second_attr_setting_value).build());
        newFilter = clusterExcludeFilters.getFilters();
        assertEquals(second_attr_setting_value, newFilter.get(SECOND_ATTRIBUTE)[0]);
        assertNull(newFilter.get(FIRST_ATTRIBUTE));

        // TEST 3: Update both settings with new values and both setting will be updated with new values
        first_attr_setting_value = "2";
        second_attr_setting_value = "def";
        service.applySettings(Settings.builder().put(first_attr_setting, first_attr_setting_value)
                .put(second_attr_setting, second_attr_setting_value)
                .build());
        newFilter = clusterExcludeFilters.getFilters();
        assertEquals(first_attr_setting_value, newFilter.get(FIRST_ATTRIBUTE)[0]);
        assertEquals(second_attr_setting_value, newFilter.get(SECOND_ATTRIBUTE)[0]);

        // TEST 4: Again update both settings but only 1 with a new value. Expectation is it should keep both the setting values, one
        // with new value and second with initial value. But it only keeps the updated setting
        first_attr_setting_value = "2";
        second_attr_setting_value = "efg";
        service.applySettings(Settings.builder().put(first_attr_setting, first_attr_setting_value)
                .put(second_attr_setting, second_attr_setting_value)
                .build());
        newFilter = clusterExcludeFilters.getFilters();
        assertNull(newFilter.get(FIRST_ATTRIBUTE));
        assertEquals(second_attr_setting_value, newFilter.get(SECOND_ATTRIBUTE)[0]);

        // TEST 5: Update to put both the setting with new values and it will keep both the settings since value is different
        // than previous ones
        first_attr_setting_value = "3";
        second_attr_setting_value = "fgh";
        service.applySettings(Settings.builder().put(first_attr_setting, first_attr_setting_value)
                .put(second_attr_setting, second_attr_setting_value)
                .build());
        newFilter = clusterExcludeFilters.getFilters();
        assertEquals(first_attr_setting_value, newFilter.get(FIRST_ATTRIBUTE)[0]);
        assertEquals(second_attr_setting_value, newFilter.get(SECOND_ATTRIBUTE)[0]);

        // TEST 6: Update one setting to put null and it removes both the setting whereas expectation is only one setting should
        // be removed for which null value is put
        service.applySettings(Settings.builder().putNull(first_attr_setting).build());
        assertNull(clusterExcludeFilters);
    }

    private void setClusterExcludeFilters(Map<String, String> filters) {
        clusterExcludeFilters = DiscoveryNodeFilters.buildFromKeyValue(OR, filters);
    }
}

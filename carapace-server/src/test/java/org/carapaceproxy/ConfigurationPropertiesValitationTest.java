/*
 Licensed to Diennea S.r.l. under one
 or more contributor license agreements. See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership. Diennea S.r.l. licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.

 */
package org.carapaceproxy;

import java.util.List;
import java.util.Properties;
import org.carapaceproxy.configstore.PropertiesConfigurationStore;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author paolo
 */
public class ConfigurationPropertiesValitationTest {

    @Test
    public void testValidationOK() {
        Properties prop = new Properties();
        prop.setProperty("listener.0.host", "0.0.0.0");
        prop.setProperty("listener.1.port", "4089");
        prop.setProperty("listener.2.ssl", "true");
        prop.setProperty("listener.11.enabled", "false");

        prop.setProperty("director.0.backends", "b1,b2,b3");

        prop.setProperty("route.0.action", "proxy-all");
        prop.setProperty("route.0.action", "cache-if-possible");
        prop.setProperty("route.0.action", "not-found");
        prop.setProperty("route.0.action", "internal-error");

        PropertiesConfigurationStore config = new PropertiesConfigurationStore(prop);

        List<ConfigurationProperties.ValidationError> errors = ConfigurationProperties.newValidator(config).validateProperties();
        assertTrue(errors.isEmpty());
    }

    @Test
    public void testValidationFail() {
        Properties prop = new Properties();
        prop.setProperty("listener.0.port", "hello"); // int required
        prop.setProperty("listener.1.ssl", "world"); // boolean required
        prop.setProperty("route.2.action", "cache"); // correct one is 'cache-if-possible'
        prop.setProperty("route.11.notexists", "cache"); // inexistent properties
        prop.setProperty("route..action", "cache-if-possible");
        prop.setProperty("route.action", "cache-if-possible");
        prop.setProperty("route.1.2.action", "cache-if-possible");
        PropertiesConfigurationStore config = new PropertiesConfigurationStore(prop);

        List<ConfigurationProperties.ValidationError> errors = ConfigurationProperties.newValidator(config).validateProperties();
        assertEquals(7, errors.size());
        System.out.println(errors);
        assertTrue(errors.toString().contains("[listener.0.port] value [hello]: invalid value. Expected: integer"));
        assertTrue(errors.toString().contains("[listener.1.ssl] value [world]: invalid value. Expected: boolean"));
        assertTrue(errors.toString().contains("[route.2.action] value [cache]: invalid value. Expected: [not-found, internal-error, proxy-all, cache-if-possible]"));
        assertTrue(errors.toString().contains("[route.11.notexists] value [cache]: property not exists"));
        assertTrue(errors.toString().contains("[route..action] value [cache-if-possible]: property not exists"));
        assertTrue(errors.toString().contains("[route.action] value [cache-if-possible]: property not exists"));
        assertTrue(errors.toString().contains("[route.1.2.action] value [cache-if-possible]: property not exists"));
    }
}

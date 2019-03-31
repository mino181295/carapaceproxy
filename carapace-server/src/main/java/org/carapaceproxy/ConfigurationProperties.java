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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.carapaceproxy.ConfigurationProperties.ValidationError.ERROR_PROPERTYNOTEXISTS;
import org.carapaceproxy.configstore.ConfigurationStore;
import static org.carapaceproxy.ConfigurationProperties.ValidationError.ERROR_PROPERTYINVALIDVALUE_EXPECTED;

/**
 *
 * Class holding all configuration properties of Carapace Proxy.
 *
 * @author paolo
 */
public final class ConfigurationProperties {

    // Possible Properties Types
    private static final String INT = "integer";
    private static final String BOOLEAN = "boolean";
    private static final String STRING = "string";
    private static final String LIST = "list";

    /*
     *   Properties
     */
    // Listeners
    public static final String LISTENER_HOST = "listener.%d.host"; // string
    public static final String LISTENER_PORT = "listener.%d.port"; // int
    public static final String LISTENER_SSLENABLE = "listener.%d.ssl"; // boolean
    public static final String LISTENER_ENABLE = "listener.%d.enabled"; // boolean
    // ...

    // Directors
    public static final String DIRECTOR_BACKENDS = "director.%d.backends"; // list of values
    // ...

    // Routes
    public static final String ROUTE_ACTION = "route.%d.action"; // value from a list of constant possible values
    // ...

    private static final Map<String, Object> AVAILABLE_PROPERTIES = Map.of(
            LISTENER_HOST, STRING,
            LISTENER_PORT, INT,
            LISTENER_SSLENABLE, BOOLEAN,
            LISTENER_ENABLE, BOOLEAN,
            DIRECTOR_BACKENDS, LIST,
            ROUTE_ACTION, Arrays.asList("not-found", "internal-error", "proxy-all", "cache-if-possible")
    );

    public static ConfigurationPropertiesValidator newValidator(ConfigurationStore config) {
        return new ConfigurationPropertiesValidator(config);
    }

    public static final class ConfigurationPropertiesValidator {

        private final ConfigurationStore config;

        private ConfigurationPropertiesValidator(ConfigurationStore config) {
            this.config = config;
        }

        public List<ValidationError> validateProperties() {
            final List<ValidationError> errors = new ArrayList<>();
            config.forEach((String p, String v) -> {
                String prop = p.replaceAll("\\.[0-9]+", ".%d");
                if (AVAILABLE_PROPERTIES.containsKey(prop)) {
                    Object type = AVAILABLE_PROPERTIES.get(prop);
                    if (type.equals(INT)) {
                        try {
                            Integer.parseInt(v);
                        } catch (NumberFormatException e) {
                            errors.add(new ValidationError(p, v, ERROR_PROPERTYINVALIDVALUE_EXPECTED + INT));
                        }
                    } else if (type.equals(BOOLEAN)) {
                        if (!"true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
                            errors.add(new ValidationError(p, v, ERROR_PROPERTYINVALIDVALUE_EXPECTED + BOOLEAN));
                        }
                    } else if (type.equals(STRING) || type.equals(LIST)) {
                        // skip, always ok
                    } else if (type instanceof List) { // set of possible values
                        List<String> values = (List<String>) type;
                        if (!values.contains(v)) {
                            errors.add(new ValidationError(p, v, ERROR_PROPERTYINVALIDVALUE_EXPECTED + values));
                        }
                    }
                } else {
                    errors.add(new ValidationError(p, v, ERROR_PROPERTYNOTEXISTS));
                }
            });
            return errors;
        }
    }

    public static final class ValidationError {

        static final String ERROR_PROPERTYNOTEXISTS = "property not exists";
        static final String ERROR_PROPERTYINVALIDVALUE_EXPECTED = "invalid value. Expected: ";

        private final String propertyName;
        private final String propertyValue;
        private final String error;

        private ValidationError(String propertyName, String propertyValue, String error) {
            this.propertyName = propertyName;
            this.propertyValue = propertyValue;
            this.error = error;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public String getPropertyValue() {
            return propertyValue;
        }

        public String getError() {
            return error;
        }

        @Override
        public String toString() {
            return "Validation error for property [" + propertyName + "] value [" + propertyValue + "]: " + error + ".";
        }

    }
}

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
package nettyhttpproxy.server.mapper;

import io.netty.handler.codec.http.HttpRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import nettyhttpproxy.EndpointMapper;
import nettyhttpproxy.MapResult;
import nettyhttpproxy.server.config.ActionConfiguration;
import nettyhttpproxy.server.config.BackendConfiguration;
import nettyhttpproxy.server.config.BackendSelector;
import nettyhttpproxy.server.config.ConfigurationNotValidException;
import nettyhttpproxy.server.config.MatchAllRequestMatcher;
import nettyhttpproxy.server.config.RequestMatcher;
import nettyhttpproxy.server.config.RouteConfiguration;
import nettyhttpproxy.server.config.RoutingKey;

/**
 * Standard Endpoint mapping
 */
public class StandardEndpointMapper extends EndpointMapper {

    private final Map<String, BackendConfiguration> backends = new HashMap<>();
    private final List<String> allbackendids = new ArrayList<>();
    private final List<RouteConfiguration> routes = new ArrayList<>();
    private final Map<String, ActionConfiguration> actions = new HashMap<>();
    private final BackendSelector backendSelector;

    public StandardEndpointMapper(BackendSelector backendSelector) {
        this.backendSelector = backendSelector;
    }

    public void configure(Properties properties) throws ConfigurationNotValidException {

        LOG.info("configured build-in action id=proxy-all");
        addAction(new ActionConfiguration("proxy-all", ActionConfiguration.TYPE_PROXY));

        for (int i = 0; i < 100; i++) {
            String prefix = "backend." + i + ".";
            String id = properties.getProperty(prefix + "id", "");
            if (!id.isEmpty()) {
                boolean enabled = Boolean.parseBoolean(properties.getProperty(prefix + "enabled", "false"));
                String host = properties.getProperty(prefix + "host", "localhost");
                int port = Integer.parseInt(properties.getProperty(prefix + "port", "8086"));
                LOG.info("configured backend " + id + " " + host + ":" + port + " enabled:" + enabled);
                if (enabled) {
                    BackendConfiguration config = new BackendConfiguration(id, host, port, "/test.html");
                    addBackend(config);
                }
            }
        }
        for (int i = 0; i < 100; i++) {
            String prefix = "route." + i + ".";
            String id = properties.getProperty(prefix + "id", "");
            if (!id.isEmpty()) {
                String action = properties.getProperty(prefix + "action", "");
                boolean enabled = Boolean.parseBoolean(properties.getProperty(prefix + "enabled", "false"));
                String match = properties.getProperty(prefix + "match", "all");
                RequestMatcher matcher;
                switch (match) {
                    case "all":
                        matcher = new MatchAllRequestMatcher();
                        break;
                    default:
                        throw new ConfigurationNotValidException(prefix + "match can be only 'all' at the moment");

                }
                LOG.log(Level.INFO, "configured route {0} action: {1} enabled: {2} matcher: {3}", new Object[]{id, action, enabled, matcher});
                RouteConfiguration config = new RouteConfiguration(id, action, enabled, matcher);
                addRoute(config);
            }
        }
    }
    private static final Logger LOG = Logger.getLogger(StandardEndpointMapper.class.getName());

    private final class RandomBackendSelector implements BackendSelector {

        @Override
        public List<String> selectBackends(HttpRequest request, RoutingKey key) {
            ArrayList<String> result = new ArrayList<>(allbackendids);
            Collections.shuffle(result);
            return result;
        }

    }

    public StandardEndpointMapper() {
        this.backendSelector = new RandomBackendSelector();
    }

    public void addBackend(BackendConfiguration backend) throws ConfigurationNotValidException {
        if (backends.put(backend.getId(), backend) != null) {
            throw new ConfigurationNotValidException("backend " + backend.getId() + " is already configured");
        }
        allbackendids.add(backend.getId());
    }

    public void addAction(ActionConfiguration action) throws ConfigurationNotValidException {
        if (actions.put(action.getId(), action) != null) {
            throw new ConfigurationNotValidException("action " + action.getId() + " is already configured");
        }
    }

    public void addRoute(RouteConfiguration route) throws ConfigurationNotValidException {
        if (routes.stream().anyMatch(s -> s.getId().equalsIgnoreCase(route.getId()))) {
            throw new ConfigurationNotValidException("route " + route.getId() + " is already configured");
        }
        routes.add(route);
    }

    @Override
    public MapResult map(HttpRequest request) {
        for (RouteConfiguration route : routes) {
            if (!route.isEnabled()) {
                continue;
            }
            RoutingKey matchResult = route.matches(request);
            if (matchResult != null) {
                ActionConfiguration action = actions.get(route.getAction());
                if (action == null) {
                    LOG.info("no action " + route.getAction() + " -> not-found for " + request.uri());
                    return MapResult.NOT_FOUND;
                }
                List<String> selectedBackends = backendSelector.selectBackends(request, matchResult);
//                LOG.info("selected " + selectedBackends + " backends for " + request.uri());
                for (String backendId : selectedBackends) {
                    switch (action.getType()) {
                        case ActionConfiguration.TYPE_PROXY: {
                            BackendConfiguration backend = this.backends.get(backendId);
                            if (backend != null && isAvailable(backend)) {
                                return new MapResult(backend.getHost(), backend.getPort(), MapResult.Action.PROXY);
                            }
                            break;
                        }
                        case ActionConfiguration.TYPE_CACHE:
                            BackendConfiguration backend = this.backends.get(backendId);
                            if (backend != null && isAvailable(backend)) {
                                return new MapResult(backend.getHost(), backend.getPort(), MapResult.Action.CACHE);
                            }
                            break;
                        default:
                            return MapResult.NOT_FOUND;
                    }
                }
            }
        }
        return MapResult.NOT_FOUND;
    }

    private boolean isAvailable(BackendConfiguration backend) {
        return true;
    }

}

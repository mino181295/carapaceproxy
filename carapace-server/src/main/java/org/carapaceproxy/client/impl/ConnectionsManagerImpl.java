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
package org.carapaceproxy.client.impl;

import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.carapaceproxy.EndpointStats;
import org.carapaceproxy.client.ConnectionsManager;
import org.carapaceproxy.client.ConnectionsManagerStats;
import org.carapaceproxy.client.EndpointConnection;
import org.carapaceproxy.client.EndpointKey;
import org.carapaceproxy.client.EndpointNotAvailableException;
import org.carapaceproxy.server.RuntimeServerConfiguration;
import org.carapaceproxy.server.RequestHandler;
import org.carapaceproxy.server.backends.BackendHealthManager;
import org.apache.commons.pool2.KeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectState;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObjectInfo;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.carapaceproxy.utils.PrometheusUtils;

/**
 * Implementation of the {@link ConnectionsManager} component
 *
 * @author enrico.olivelli
 */
public class ConnectionsManagerImpl implements ConnectionsManager, AutoCloseable {

    private final GenericKeyedObjectPool<EndpointKey, EndpointConnectionImpl> connections;
    private int idleTimeout;
    private int stuckRequestTimeout;
    private int connectTimeout;
    private int borrowTimeout;
    private final ConcurrentHashMap<EndpointKey, EndpointStats> endpointsStats = new ConcurrentHashMap<>();
    private final EventLoopGroup group;
    private final EventLoopGroup eventLoopForOutboundConnections;

    private ScheduledFuture<?> stuckRequestsReaperFuture;
    private ConcurrentHashMap<Long, RequestHandler> pendingRequests = new ConcurrentHashMap<>();

    final BackendHealthManager backendHealthManager;
    final ScheduledExecutorService scheduler;

    private static final Gauge PENDING_REQUESTS_GAUGE = PrometheusUtils.createGauge("backends", "pending_requests",
            "pending requests").register();
    private static final Counter STUCK_REQUESTS_COUNTER = PrometheusUtils.createCounter("backends",
            "stuck_requests_total",
            "stuck requests, this requests will be killed").register();

    @VisibleForTesting
    public void returnConnection(EndpointConnectionImpl con) {
        LOG.log(Level.INFO, "returnConnection:{0}", con);
        connections.returnObject(con.getKey(), con);
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    private final class ConnectionsFactory implements KeyedPooledObjectFactory<EndpointKey, EndpointConnectionImpl> {

        @Override
        public PooledObject<EndpointConnectionImpl> makeObject(EndpointKey k) throws Exception {
            EndpointStats endpointstats = endpointsStats.computeIfAbsent(k, EndpointStats::new);
            EndpointConnectionImpl con = new EndpointConnectionImpl(k, ConnectionsManagerImpl.this, endpointstats);
            LOG.log(Level.INFO, "opened new connection {0}", new Object[]{con});
            return new DefaultPooledObject<>(con);
        }

        @Override
        public void destroyObject(EndpointKey k, PooledObject<EndpointConnectionImpl> po) throws Exception {
            LOG.log(Level.INFO, "destroy con {0} {1}", new Object[]{k, po.getObject()});
            po.getObject().destroy();
        }

        @Override
        public boolean validateObject(EndpointKey k, PooledObject<EndpointConnectionImpl> po) {
            PooledObjectState state = po.getState();
            switch (state) {
                case ABANDONED:
                case IDLE:
                case EVICTION:
                    LOG.log(Level.INFO, "validateObject {2} {0} {1} ", new Object[]{k, po.getObject(), state});
                    break;
                default:
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.log(Level.FINE, "validateObject {2} {0} {1} ", new Object[]{k, po.getObject(), state});
                    }
            }
            String validationResult = po.getObject().validate();
            if (validationResult != null) {
                LOG.log(Level.WARNING, "validateObject {0} {1}-> {2}", new Object[]{k, po.getObject(), validationResult});
            }
            return validationResult == null;
        }

        @Override
        public void activateObject(EndpointKey k, PooledObject<EndpointConnectionImpl> po) throws Exception {
//            LOG.log(Level.INFO, "activateObject {0} {1}", new Object[]{k, po.getObject()});
        }

        @Override
        public void passivateObject(EndpointKey k, PooledObject<EndpointConnectionImpl> po) throws Exception {
//            LOG.log(Level.INFO, "passivateObject {0} {1}", new Object[]{k, po.getObject()});
        }

    }

    void registerPendingRequest(RequestHandler handler) {
        pendingRequests.put(handler.getId(), handler);
        PENDING_REQUESTS_GAUGE.inc();
    }

    void unregisterPendingRequest(RequestHandler clientSidePeerHandler) {
        if (clientSidePeerHandler == null) {
            return;
        }
        RequestHandler removed = pendingRequests.remove(clientSidePeerHandler.getId());
        if (removed != null) {
            PENDING_REQUESTS_GAUGE.dec();
        }
    }

    @VisibleForTesting
    public Gauge getPENDING_REQUESTS_GAUGE() {
        return PENDING_REQUESTS_GAUGE;
    }

    private class RequestHandlerChecker implements Runnable {

        @Override
        public void run() {
            long now = System.currentTimeMillis();
            List<RequestHandler> toRemove = new ArrayList<>();
            for (Map.Entry<Long, RequestHandler> entry : pendingRequests.entrySet()) {
                RequestHandler requestHandler = entry.getValue();
                requestHandler.failIfStuck(now, stuckRequestTimeout, () -> {
                    EndpointConnection connectionToEndpoint = requestHandler.getConnectionToEndpoint();
                    if (connectionToEndpoint != null) {
                        backendHealthManager.reportBackendUnreachable(
                                connectionToEndpoint.getKey().getHostPort(), now,
                                "a request to " + requestHandler.getUri() + " for user " + requestHandler.getUserId() + " appears stuck");
                    }
                    STUCK_REQUESTS_COUNTER.inc();
                    toRemove.add(entry.getValue());
                });
            }
            toRemove.forEach(r -> {
                unregisterPendingRequest(r);
            });
        }

    }

    @Override
    public final void applyNewConfiguration(RuntimeServerConfiguration configuration) {
        int oldIdleTimeout = this.idleTimeout;
        this.idleTimeout = configuration.getIdleTimeout();
        this.stuckRequestTimeout = configuration.getStuckRequestTimeout();
        this.connectTimeout = configuration.getConnectTimeout();
        this.borrowTimeout = configuration.getBorrowTimeout();
        int maxConnectionsPerEndpoint = configuration.getMaxConnectionsPerEndpoint();
        connections.setMaxTotalPerKey(maxConnectionsPerEndpoint);
        connections.setMaxIdlePerKey(maxConnectionsPerEndpoint);
        connections.setMaxTotal(-1);

        if (this.stuckRequestsReaperFuture != null && (oldIdleTimeout != idleTimeout)) {
            this.stuckRequestsReaperFuture.cancel(false);
            LOG.log(Level.INFO,"Re-scheduling stuckRequestsReaper with period (idleTimeout/4):" +
                    (idleTimeout / 4) + " ms");
            this.stuckRequestsReaperFuture =
                    this.scheduler.scheduleWithFixedDelay(new RequestHandlerChecker(), idleTimeout / 4, idleTimeout / 4,
                            TimeUnit.MILLISECONDS);
        }

    }

    public ConnectionsManagerImpl(RuntimeServerConfiguration configuration, BackendHealthManager backendHealthManager) {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        GenericKeyedObjectPoolConfig config = new GenericKeyedObjectPoolConfig();
        config.setTestOnReturn(true);
        config.setTestOnBorrow(true);
        config.setTestWhileIdle(true);
        config.setBlockWhenExhausted(true);
        config.setJmxEnabled(false);
        group = Epoll.isAvailable() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
        eventLoopForOutboundConnections =  Epoll.isAvailable() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
        connections = new GenericKeyedObjectPool<>(new ConnectionsFactory(), config);
        this.backendHealthManager = backendHealthManager;
        applyNewConfiguration(configuration);
    }

    EventLoopGroup getEventLoopForOutboundConnections() {
        return eventLoopForOutboundConnections;
    }

    @Override
    public EndpointConnection getConnection(EndpointKey key) throws EndpointNotAvailableException {
        long _start = System.currentTimeMillis();
        try {
            EndpointConnection result = connections.borrowObject(key, borrowTimeout);
            return result;
        } catch (NoSuchElementException ex) {
            if (LOG.isLoggable(Level.FINER)) {
                long delta = System.currentTimeMillis() - _start;
                LOG.log(Level.FINER,
                        "Too many connections to " + key + " and/or cannot create a new connection (elapsed "
                        + delta + ", borrowTimeout " + borrowTimeout + ")", ex);
                connections.listAllObjects().forEach((k, list) -> {
                    if (list != null) {
                        list.forEach(po -> {
                            LOG.log(Level.FINER, "current {0} con {1}", new Object[]{k, po.getPooledObjectToString()});
                        });
                    }
                });
            }
            throw new EndpointNotAvailableException("Too many connections to " + key
                    + " and/or cannot create a new connection ("+ex.getMessage()+")", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EndpointNotAvailableException("Interrupted while borrowing a connection from the pool for key "+key, ex);
        } catch (ConnectException ex) {
            throw new EndpointNotAvailableException("Endpoint error while borrowing a connection from the pool for key "+key, ex);
        } catch (Exception ex) {
            LOG.log(Level.SEVERE,"Internal error while borrowing a connection for " + key, ex);
            throw new EndpointNotAvailableException(ex);
        }
    }
    private static final Logger LOG = Logger.getLogger(ConnectionsManagerImpl.class.getName());

    @Override
    public void start() {
        LOG.log(Level.INFO, "Scheduling stuckRequestsReaper with period (idleTimeout/4):" + (idleTimeout / 4) + " ms");
        this.stuckRequestsReaperFuture =
                this.scheduler.scheduleWithFixedDelay(new RequestHandlerChecker(), idleTimeout / 4, idleTimeout / 4,
                        TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        if (stuckRequestsReaperFuture != null) {
            stuckRequestsReaperFuture.cancel(true);
        }
        scheduler.shutdown();
        Map<String, List<DefaultPooledObjectInfo>> all = connections.listAllObjects();
        LOG.fine("[POOL] numIdle: " + connections.getNumIdle() + " numActive: " + connections.getNumActive());
        connections.clear();
        LOG.info("[POOL] after close numIdle: " + connections.getNumIdle() + " numActive: " + connections.getNumActive());

        all.forEach((key, value) -> {
            LOG.fine("[POOL] " + key + " -> " + value.size() + " connections");

            for (DefaultPooledObjectInfo info : value) {
                LOG.fine("[POOL] " + key + " -> " + info.getPooledObjectToString());
            }
        });

        connections.close();
        group.shutdownGracefully();
        eventLoopForOutboundConnections.shutdownGracefully();
    }

    final ConnectionsManagerStats stats = new ConnectionsManagerStats() {
        @Override
        public Map<EndpointKey, EndpointStats> getEndpoints() {
            return Collections.unmodifiableMap(endpointsStats);
        }
    };

    @Override
    public ConnectionsManagerStats getStats() {
        return stats;
    }

    @VisibleForTesting
    public int getIdleTimeout() {
        return idleTimeout;
    }

    @VisibleForTesting
    public int getBorrowTimeout() {
        return borrowTimeout;
    }

    @VisibleForTesting
    public ScheduledFuture<?> getStuckRequestsReaperFuture() {
        return stuckRequestsReaperFuture;
    }

    @VisibleForTesting
    public GenericKeyedObjectPool<EndpointKey, EndpointConnectionImpl> getConnections() {
        return connections;
    }

}

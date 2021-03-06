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
package org.carapaceproxy.server.config;

import java.util.Objects;

/**
 * Listens for connections on the network
 */
public class NetworkListenerConfiguration {

    private final String host;
    private final int port;
    private final boolean ssl;
    private final boolean ocps;
    private final String sslCiphers;
    private final String defaultCertificate;
    private final String sslTrustoreFile;
    private final String sslTrustorePassword;

    public HostPort getKey() {
        return new HostPort(host, port);
    }

    public static final class HostPort {

        private final String host;
        private final int port;

        public HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 43 * hash + Objects.hashCode(this.host);
            hash = 43 * hash + this.port;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final HostPort other = (HostPort) obj;
            if (this.port != other.port) {
                return false;
            }
            if (!Objects.equals(this.host, other.host)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "HostPort{" + "host=" + host + ", port=" + port + '}';
        }

    }

    public NetworkListenerConfiguration(String host, int port) {
        this.host = host;
        this.port = port;
        this.ssl = false;
        this.sslCiphers = null;
        this.ocps = false;
        this.defaultCertificate = null;
        this.sslTrustoreFile = null;
        this.sslTrustorePassword = null;
    }

    public NetworkListenerConfiguration(String host, int port, boolean ssl,
            boolean ocps, String sslCiphers, String defaultCertificate, String sslTrustoreFile, String sslTrustorePassword) {
        this.host = host;
        this.port = port;
        this.ssl = ssl;
        this.defaultCertificate = defaultCertificate;
        this.sslCiphers = sslCiphers;
        this.ocps = ocps;
        this.sslTrustoreFile = sslTrustoreFile;
        this.sslTrustorePassword = sslTrustorePassword;
    }

    public String getSslTrustoreFile() {
        return sslTrustoreFile;
    }

    public String getSslTrustorePassword() {
        return sslTrustorePassword;
    }

    public String getDefaultCertificate() {
        return defaultCertificate;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isSsl() {
        return ssl;
    }

    public boolean isOcps() {
        return ocps;
    }

    public String getSslCiphers() {
        return sslCiphers;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + Objects.hashCode(this.host);
        hash = 29 * hash + this.port;
        hash = 29 * hash + (this.ssl ? 1 : 0);
        hash = 29 * hash + (this.ocps ? 1 : 0);
        hash = 29 * hash + Objects.hashCode(this.sslCiphers);
        hash = 29 * hash + Objects.hashCode(this.defaultCertificate);
        hash = 29 * hash + Objects.hashCode(this.sslTrustoreFile);
        hash = 29 * hash + Objects.hashCode(this.sslTrustorePassword);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NetworkListenerConfiguration other = (NetworkListenerConfiguration) obj;
        if (this.port != other.port) {
            return false;
        }
        if (this.ssl != other.ssl) {
            return false;
        }
        if (this.ocps != other.ocps) {
            return false;
        }
        if (!Objects.equals(this.host, other.host)) {
            return false;
        }
        if (!Objects.equals(this.sslCiphers, other.sslCiphers)) {
            return false;
        }
        if (!Objects.equals(this.defaultCertificate, other.defaultCertificate)) {
            return false;
        }
        if (!Objects.equals(this.sslTrustoreFile, other.sslTrustoreFile)) {
            return false;
        }
        if (!Objects.equals(this.sslTrustorePassword, other.sslTrustorePassword)) {
            return false;
        }
        return true;
    }

}

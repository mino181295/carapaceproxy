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
package org.carapaceproxy.api;

import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.carapaceproxy.configstore.CertificateData;
import org.carapaceproxy.server.HttpProxyServer;
import org.carapaceproxy.server.RuntimeServerConfiguration;
import org.carapaceproxy.server.certiticates.DynamicCertificateState;
import static org.carapaceproxy.server.certiticates.DynamicCertificateState.AVAILABLE;
import static org.carapaceproxy.server.certiticates.DynamicCertificateState.WAITING;
import org.carapaceproxy.server.certiticates.DynamicCertificatesManager;
import org.carapaceproxy.server.config.SSLCertificateConfiguration;
import org.carapaceproxy.server.config.SSLCertificateConfiguration.CertificateMode;
import static org.carapaceproxy.server.config.SSLCertificateConfiguration.CertificateMode.ACME;
import static org.carapaceproxy.server.config.SSLCertificateConfiguration.CertificateMode.MANUAL;
import static org.carapaceproxy.server.config.SSLCertificateConfiguration.CertificateMode.STATIC;
import org.carapaceproxy.utils.CertificatesUtils;

/**
 * Access to certificates
 *
 * @author matteo.minardi
 */
@Path("/certificates")
@Produces("application/json")
public class CertificatesResource {

    @javax.ws.rs.core.Context
    ServletContext context;

    public static final class CertificateBean {

        private final String id;
        private final String hostname;
        private final String mode;
        private final boolean dynamic;
        private String status;
        private final String sslCertificateFile;

        public CertificateBean(String id, String hostname, String mode, boolean dynamic, String sslCertificateFile) {
            this.id = id;
            this.hostname = hostname;
            this.mode = mode;
            this.dynamic = dynamic;
            this.sslCertificateFile = sslCertificateFile;
        }

        public String getId() {
            return id;
        }

        public String getHostname() {
            return hostname;
        }

        public String getMode() {
            return mode;
        }

        public boolean isDynamic() {
            return dynamic;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getSslCertificateFile() {
            return sslCertificateFile;
        }
    }

    @GET
    @Path("/")
    public Map<String, CertificateBean> getAllCertificates() {
        HttpProxyServer server = (HttpProxyServer) context.getAttribute("server");
        RuntimeServerConfiguration conf = server.getCurrentConfiguration();
        DynamicCertificatesManager dynamicCertificateManager = server.getDynamicCertificateManager();
        Map<String, CertificateBean> res = new HashMap<>();
        for (Map.Entry<String, SSLCertificateConfiguration> certificateEntry : conf.getCertificates().entrySet()) {
            SSLCertificateConfiguration certificate = certificateEntry.getValue();
            CertificateBean certBean = new CertificateBean(
                    certificate.getId(),
                    certificate.getHostname(),
                    stateToStatusString(certificate.getMode()),
                    certificate.isDynamic(),
                    certificate.getFile()
            );

            if (certificate.isDynamic()) {
                DynamicCertificateState state = dynamicCertificateManager.getStateOfCertificate(certBean.getId());
                certBean.setStatus(stateToStatusString(state));
            }
            res.put(certificateEntry.getKey(), certBean);
        }

        return res;
    }

    @GET
    @Path("{certId}")
    public CertificateBean getCertificateById(@PathParam("certId") String certId) {
        return findCertificateById(certId);
    }

    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Path("{certId}/download")
    public Response downloadCertificateById(@PathParam("certId") String certId) throws GeneralSecurityException {
        CertificateBean cert = findCertificateById(certId);
        byte[] data = new byte[0];
        if (cert != null && cert.isDynamic()) {
            HttpProxyServer server = (HttpProxyServer) context.getAttribute("server");
            DynamicCertificatesManager dynamicCertificateManager = server.getDynamicCertificateManager();
            data = dynamicCertificateManager.getCertificateForDomain(cert.hostname);
        }

        return Response
                .ok(data, MediaType.APPLICATION_OCTET_STREAM)
                .header("content-disposition", "attachment; filename = " + cert.hostname + ".p12")
                .build();
    }

    private CertificateBean findCertificateById(String certId) {
        HttpProxyServer server = (HttpProxyServer) context.getAttribute("server");
        RuntimeServerConfiguration conf = server.getCurrentConfiguration();
        Map<String, SSLCertificateConfiguration> certificateList = conf.getCertificates();
        if (certificateList.containsKey(certId)) {
            SSLCertificateConfiguration certificate = certificateList.get(certId);
            CertificateBean certBean = new CertificateBean(
                    certificate.getId(),
                    certificate.getHostname(),
                    stateToStatusString(certificate.getMode()),
                    certificate.isDynamic(),
                    certificate.getFile()
            );

            if (certificate.isDynamic()) {
                DynamicCertificateState state = server.getDynamicCertificateManager().getStateOfCertificate(certBean.getId());
                certBean.setStatus(stateToStatusString(state));
            }
            return certBean;
        }

        return null;
    }

    static String stateToStatusString(DynamicCertificateState state) {
        if (state == null) {
            return "unknown";
        }
        switch (state) {
            case WAITING:
                return "waiting"; // certificate waiting for issuing/renews
            case VERIFYING:
                return "verifying"; // challenge verification by LE pending
            case VERIFIED:
                return "verified"; // challenge succeded
            case ORDERING:
                return "ordering"; // certificate order pending
            case REQUEST_FAILED:
                return "request failed"; // challenge/order failed
            case AVAILABLE:
                return "available";// certificate available(saved) and not expired
            case EXPIRED:     // certificate expired
                return "expired";
            default:
                return "unknown";
        }
    }

    static String stateToStatusString(CertificateMode mode) {
        if (mode == null) {
            return "unknown";
        }
        switch (mode) {
            case STATIC:
                return "static";
            case ACME:
                return "acme";
            case MANUAL:
                return "manual";
            default:
                return "unknown";
        }
    }

    @POST
    @Path("{domain}/upload")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response uploadCertificate(@PathParam("domain") String domain, InputStream uploadedInputStream) throws Exception {

        byte[] data = uploadedInputStream.readAllBytes();

        // Validation
        if (!CertificatesUtils.validateKeystore(data)) {
            return Response.status(422).entity("ERROR: unable to read uploded certificate.").build();
        }

        CertificateData cert = new CertificateData(
                domain, "", Base64.getEncoder().encodeToString(data), AVAILABLE.name(), "", "", true
        );
        cert.setManual(true);

        HttpProxyServer server = (HttpProxyServer) context.getAttribute("server");
        server.createDynamicCertificateForDomain(cert);

        return Response.status(200).entity("SUCCESS: Certificate saved as manual.").build();
    }

}

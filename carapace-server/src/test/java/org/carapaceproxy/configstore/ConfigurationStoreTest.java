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
package org.carapaceproxy.configstore;

import java.net.URL;
import java.security.KeyPair;
import java.util.Properties;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.bookkeeper.stats.NullStatsLogger;
import org.carapaceproxy.server.certiticates.DynamicCertificateState;
import static org.carapaceproxy.server.certiticates.DynamicCertificatesManager.DEFAULT_KEYPAIRS_SIZE;
import org.carapaceproxy.server.config.ConfigurationNotValidException;
import static org.carapaceproxy.utils.TestUtils.assertEqualsKey;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.shredzone.acme4j.toolbox.JSON;
import org.shredzone.acme4j.util.KeyPairUtils;

/**
 * Test for {@link PropertiesConfigurationStore} and {@link HerdDBConfigurationStore}.
 *
 * @author paolo.venturi
 */
@RunWith(JUnitParamsRunner.class)
public class ConfigurationStoreTest {

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    private ConfigurationStore store;
    private static final String d1 = "localhost1";
    private static final String d2 = "localhost2";
    private static final String d3 = "localhost3";

    @After
    public void after() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    @Parameters({"in-memory", "db"})
    public void testConfigurationStore(String type) throws Exception {
        Properties props = new Properties();
        props.setProperty("certificate.0.hostname", d1);
        props.setProperty("certificate.0.dynamic", "true");
        props.setProperty("certificate.1.hostname", d2);
        props.setProperty("certificate.1.dynamic", "true");
        props.put("db.jdbc.url", "jdbc:herddb:localhost");

        store = new PropertiesConfigurationStore(props);
        if (type.equals("db")) {
            store = new HerdDBConfigurationStore(store, false, null, tmpDir.getRoot(), NullStatsLogger.INSTANCE);
        } else {
            assertEquals(2, store.asProperties("certificate.1").size());
            assertEquals(5, store.asProperties(null).size());
            assertEquals(2, store.nextIndexFor("certificate"));
            assertEquals(0, store.nextIndexFor("unknown"));
            assertEquals(true, store.anyPropertyMatches(
                    (k, v) -> k.matches("certificate\\.[0-9]+\\.hostname") && v.equals(d1)
            ));
            assertEquals(false, store.anyPropertyMatches(
                    (k, v) -> k.matches("certificate\\.[0-9]+\\.hostname") && v.equals("unknown")
            ));
        }

        testKeyPairOperations();
        testCertificateOperations();
        testAcmeChallengeTokens();
    }

    private void testKeyPairOperations() {
        // KeyPairs generation + saving
        KeyPair acmePair = KeyPairUtils.createKeyPair(DEFAULT_KEYPAIRS_SIZE);
        store.saveAcmeUserKey(acmePair);
        store.saveAcmeUserKey(KeyPairUtils.createKeyPair(DEFAULT_KEYPAIRS_SIZE)); // key not overwritten

        store.saveKeyPairForDomain(KeyPairUtils.createKeyPair(DEFAULT_KEYPAIRS_SIZE), d1, true);
        KeyPair domain1Pair = KeyPairUtils.createKeyPair(DEFAULT_KEYPAIRS_SIZE);
        store.saveKeyPairForDomain(domain1Pair, d1, true); // key overwritten

        KeyPair domain2Pair = KeyPairUtils.createKeyPair(DEFAULT_KEYPAIRS_SIZE);
        store.saveKeyPairForDomain(domain2Pair, d2, false);
        store.saveKeyPairForDomain(KeyPairUtils.createKeyPair(DEFAULT_KEYPAIRS_SIZE), d2, false); // key not overwritten

        // KeyPairs loading
        KeyPair loadedPair = store.loadAcmeUserKeyPair();
        assertEqualsKey(acmePair.getPrivate(), loadedPair.getPrivate());
        assertEqualsKey(acmePair.getPublic(), loadedPair.getPublic());

        loadedPair = store.loadKeyPairForDomain(d1);
        assertEqualsKey(domain1Pair.getPrivate(), loadedPair.getPrivate());
        assertEqualsKey(domain1Pair.getPublic(), loadedPair.getPublic());

        loadedPair = store.loadKeyPairForDomain(d2);
        assertEqualsKey(domain2Pair.getPrivate(), loadedPair.getPrivate());
        assertEqualsKey(domain2Pair.getPublic(), loadedPair.getPublic());
    }

    private void testCertificateOperations() throws Exception {
        String order = new URL("http://locallhost/order").toString();
        String challenge = JSON.parse("{\"challenge\": \"data\"}").toString();

        // Certificates saving
        CertificateData cert1 = new CertificateData(
                d1, "encodedPK1", "encodedChain1", DynamicCertificateState.AVAILABLE.name(), order, challenge, true
        );
        store.saveCertificate(cert1);

        CertificateData cert2 = new CertificateData(
                d2, "encodedPK2", "encodedChain2", DynamicCertificateState.WAITING.name(), null, null, false
        );
        store.saveCertificate(cert2);

        // Certificates loading
        assertEquals(cert1, store.loadCertificateForDomain(d1));
        assertEquals(cert2, store.loadCertificateForDomain(d2));

        // Cert Updating
        cert1.setAvailable(false);
        cert1.setState(DynamicCertificateState.WAITING.name());
        cert1.setPendingOrderLocation(new URL("http://locallhost/updatedorder").toString());
        cert1.setPendingChallengeData(JSON.parse("{\"challenge\": \"updateddata\"}").toString());
        store.saveCertificate(cert1);
        assertEquals(cert1, store.loadCertificateForDomain(d1));
    }

    private void testAcmeChallengeTokens() {
        store.saveAcmeChallengeToken("token-id", "token-data");
        store.saveAcmeChallengeToken("token-id2", "token-data2");
        assertEquals("token-data", store.loadAcmeChallengeToken("token-id"));
        assertEquals("token-data2", store.loadAcmeChallengeToken("token-id2"));
        store.deleteAcmeChallengeToken("token-id");
        assertNull(store.loadAcmeChallengeToken("token-id"));
        store.deleteAcmeChallengeToken("token-id2");
        assertNull(store.loadAcmeChallengeToken("token-id2"));
    }

    @Test
    public void testPersistentConfiguration() throws ConfigurationNotValidException {
        Properties props = new Properties();
        props.setProperty("certificate.0.hostname", d1);
        props.setProperty("certificate.0.dynamic", "true");
        props.setProperty("certificate.1.hostname", d2);
        props.setProperty("certificate.1.dynamic", "true");
        props.put("db.jdbc.url", "jdbc:herddb:localhost");

        PropertiesConfigurationStore propertiesConfigurationStore = new PropertiesConfigurationStore(props);

        store = new HerdDBConfigurationStore(propertiesConfigurationStore, false, null, tmpDir.getRoot(), NullStatsLogger.INSTANCE);

        // Check applied configuration (loaded from empty db NB: passed configuration is ignored)
        assertEquals("", store.getProperty("certificate.0.hostname", ""));
        assertEquals("", store.getProperty("certificate.0.dynamic", ""));
        assertEquals("", store.getProperty("certificate.1.hostname", ""));
        assertEquals("", store.getProperty("certificate.1.dynamic", ""));

        // Apply first configuration
        store.commitConfiguration(propertiesConfigurationStore);

        // Check cached applied configuration
        assertEquals(d1, store.getProperty("certificate.0.hostname", ""));
        assertEquals("true", store.getProperty("certificate.0.dynamic", ""));
        assertEquals(d2, store.getProperty("certificate.1.hostname", ""));
        assertEquals("true", store.getProperty("certificate.1.dynamic", ""));

        assertEquals(2, store.asProperties("certificate.1").size());
        assertEquals(5, store.asProperties(null).size());
        assertEquals(2, store.nextIndexFor("certificate"));
        assertEquals(0, store.nextIndexFor("unknown"));
        assertEquals(true, store.anyPropertyMatches(
                (k, v) -> k.matches("certificate\\.[0-9]+\\.hostname") && v.equals(d1)
        ));
        assertEquals(false, store.anyPropertyMatches(
                (k, v) -> k.matches("certificate\\.[0-9]+\\.hostname") && v.equals("unknown")
        ));

        // New configuration to apply
        props = new Properties();
        // no more certificate.0.*
        props.setProperty("certificate.1.hostname", d1); // changed from d2 ("localhost2")
        props.setProperty("certificate.1.dynamic", "false"); // changed from "true"
        props.setProperty("certificate.3.hostname", d3); // new
        props.setProperty("certificate.3.dynamic", "true"); // new
        propertiesConfigurationStore = new PropertiesConfigurationStore(props);

        store.commitConfiguration(propertiesConfigurationStore);

        // check new configuration has been applied successfully
        checkConfiguration();
        store.close();

        // Loading stored configuration
        props = new Properties();
        props.put("db.jdbc.url", "jdbc:herddb:localhost");
        propertiesConfigurationStore = new PropertiesConfigurationStore(props);
        store = new HerdDBConfigurationStore(propertiesConfigurationStore, false, null, tmpDir.getRoot(), NullStatsLogger.INSTANCE);
        checkConfiguration();
    }

    private void checkConfiguration() {
        // check new configuration has been applied successfully
        assertEquals("", store.getProperty("certificate.0.hostname", ""));
        assertEquals("", store.getProperty("certificate.0.dynamic", ""));
        assertEquals(d1, store.getProperty("certificate.1.hostname", ""));
        assertEquals("false", store.getProperty("certificate.1.dynamic", ""));
        assertEquals(d3, store.getProperty("certificate.3.hostname", ""));
        assertEquals("true", store.getProperty("certificate.3.dynamic", ""));

        assertEquals(2, store.asProperties("certificate.1.").size());
        assertEquals(2, store.asProperties("certificate.3.").size());
        assertEquals(4, store.asProperties(null).size());
        assertEquals(4, store.nextIndexFor("certificate"));
        assertEquals(0, store.nextIndexFor("unknown"));

        assertEquals(true, store.anyPropertyMatches(
                (k, v) -> k.matches("certificate\\.[0-9]+\\.hostname") && v.equals(d1)
        ));
        assertEquals(true, store.anyPropertyMatches(
                (k, v) -> k.matches("certificate\\.[0-9]+\\.hostname") && v.equals(d3)
        ));
        assertEquals(false, store.anyPropertyMatches(
                (k, v) -> k.matches("certificate\\.[0-9]+\\.hostname") && v.equals("unknown")
        ));
    }

}

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
package nettyhttpproxy.launcher;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.LogManager;
import nettyhttpproxy.configstore.ConfigurationStore;
import nettyhttpproxy.configstore.PropertiesConfigurationStore;
import nettyhttpproxy.server.HttpProxyServer;

/**
 * Created by enrico.olivelli on 23/03/2015.
 */
public class ServerMain implements AutoCloseable {

    static {
        // see https://github.com/netty/netty/pull/7650
        if (System.getProperty("io.netty.tryReflectionSetAccessible") == null) {
            System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        }
    }

    private static final Logger LOG = Logger.getLogger(ServerMain.class.getName());
    private final static CountDownLatch running = new CountDownLatch(1);

    private final ConfigurationStore configuration;
    private final PidFileLocker pidFileLocker;
    private HttpProxyServer server;
    private boolean started;
    private final File basePath;

    private static ServerMain runningInstance;

    public ServerMain(ConfigurationStore configuration, File basePath) {
        this.configuration = configuration;
        this.pidFileLocker = new PidFileLocker(basePath.toPath().toAbsolutePath());
        this.basePath = basePath;
    }

    @Override
    public void close() {

        if (server != null) {
            try {
                server.close();
            } catch (Exception ex) {
                Logger.getLogger(ServerMain.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                server = null;
            }
        }
        pidFileLocker.close();
        running.countDown();
    }

    public static void main(String... args) {
        try {
            Properties configuration = new Properties();
            File basePath = new File(System.getProperty("user.dir", "."));
            boolean configFileFromParameter = false;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (!arg.startsWith("-")) {
                    File configFile = new File(args[i]).getAbsoluteFile();
                    LOG.severe("Reading configuration from " + configFile);
                    try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
                        configuration.load(reader);
                    }
                    basePath = configFile.getParentFile().getParentFile();
                    configFileFromParameter = true;
                } else if (arg.equals("--use-env")) {
                    System.getenv().forEach((key, value) -> {
                        System.out.println("Considering env as system property " + key + " -> " + value);
                        System.setProperty(key, value);
                    });
                } else if (arg.startsWith("-D")) {
                    int equals = arg.indexOf('=');
                    if (equals > 0) {
                        String key = arg.substring(2, equals);
                        String value = arg.substring(equals + 1);
                        System.setProperty(key, value);
                    }
                }
            }
            if (!configFileFromParameter) {
                File configFile = new File("conf/server.properties").getAbsoluteFile();
                System.out.println("Reading configuration from " + configFile);
                if (configFile.isFile()) {
                    try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
                        configuration.load(reader);
                    }
                    basePath = configFile.getParentFile().getParentFile();
                }
            }

            System.getProperties().forEach((k, v) -> {
                String key = k + "";
                if (!key.startsWith("java") && !key.startsWith("user")) {
                    configuration.put(k, v);
                }
            });

            LogManager.getLogManager().readConfiguration();

            Runtime.getRuntime().addShutdownHook(new Thread("ctrlc-hook") {

                @Override
                public void run() {
                    System.out.println("Ctrl-C trapped. Shutting down");
                    ServerMain _brokerMain = runningInstance;
                    if (_brokerMain != null) {
                        _brokerMain.close();
                    }
                }

            });
            ConfigurationStore configurationStore = new PropertiesConfigurationStore(configuration);
            runningInstance = new ServerMain(configurationStore, basePath);
            runningInstance.start();

            runningInstance.join();

        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    public boolean isStarted() {
        return started;
    }

    public static ServerMain getRunningInstance() {
        return runningInstance;
    }

    public HttpProxyServer getServer() {
        return server;
    }

    public void join() {
        try {
            running.await();
        } catch (InterruptedException discard) {
        }
        started = false;
    }

    public void start() throws Exception {
        pidFileLocker.lock();

        server = new HttpProxyServer(null, basePath);
        server.configureAtBoot(configuration);
        server.start();
        server.startMetrics();
        server.startAdminInterface();
        started = true;
    }

}

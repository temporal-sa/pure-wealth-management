package io.temporal.demo.wealthmanagement.common;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.SimpleSslContextBuilder;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

import javax.net.ssl.SSLException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The client the status activity uses to signal the parent chat workflow. The worker itself
 * uses the Spring-configured client (application*.yaml); this one is built from the same
 * environment variables, with an API key or an mTLS certificate and key when they are set.
 */
public final class TemporalClient {

    private static volatile WorkflowClient client;

    private TemporalClient() {
    }

    /** One client for the process, created on first use. */
    public static WorkflowClient get() throws IOException {
        if (client == null) {
            synchronized (TemporalClient.class) {
                if (client == null) {
                    System.out.println("Temporal client for status updates: " + ServerInfo.getServerInfo());
                    client = WorkflowClient.newInstance(serviceStubs(),
                            WorkflowClientOptions.newBuilder().setNamespace(ServerInfo.getNamespace()).build());
                }
            }
        }
        return client;
    }

    static WorkflowServiceStubsOptions serviceStubsOptions() throws IOException {
        WorkflowServiceStubsOptions.Builder builder =
                WorkflowServiceStubsOptions.newBuilder().setTarget(ServerInfo.getAddress());
        String apiKey = ServerInfo.getApiKey();
        if (!apiKey.isEmpty()) {
            builder.addApiKey(() -> apiKey).setEnableHttps(true);
        } else if (!ServerInfo.getCertPath().isEmpty() && !ServerInfo.getKeyPath().isEmpty()) {
            builder.setSslContext(sslContext());
        }
        return builder.build();
    }

    private static WorkflowServiceStubs serviceStubs() throws IOException {
        return WorkflowServiceStubs.newServiceStubs(serviceStubsOptions());
    }

    private static io.grpc.netty.shaded.io.netty.handler.ssl.SslContext sslContext() throws IOException, SSLException {
        try (InputStream cert = new FileInputStream(ServerInfo.getCertPath());
             InputStream key = new FileInputStream(ServerInfo.getKeyPath())) {
            return SimpleSslContextBuilder.forPKCS8(cert, key).build();
        }
    }
}

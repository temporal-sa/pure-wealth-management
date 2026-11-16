package io.temporal.demo.wealthmanagement.common;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.converter.DataConverter;
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
 * environment variables, with an API key or an mTLS certificate and key when they are set,
 * and the same data converter as the worker's client (the claim check codec, when it is on).
 */
public final class TemporalClient {

    private static volatile WorkflowClient client;
    private static volatile DataConverter dataConverter;

    private TemporalClient() {
    }

    /** Set by claimcheck/TemporalOptionsConfig when claim check is on, before any status is sent. */
    public static void setDataConverter(DataConverter converter) {
        dataConverter = converter;
    }

    static WorkflowClientOptions clientOptions() {
        WorkflowClientOptions.Builder builder = WorkflowClientOptions.newBuilder().setNamespace(ServerInfo.getNamespace());
        if (dataConverter != null) {
            builder.setDataConverter(dataConverter);
        }
        return builder.build();
    }

    /** One client for the process, created on first use. */
    public static WorkflowClient get() throws IOException {
        if (client == null) {
            synchronized (TemporalClient.class) {
                if (client == null) {
                    System.out.println("Temporal client for status updates: " + ServerInfo.getServerInfo());
                    client = WorkflowClient.newInstance(serviceStubs(), clientOptions());
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

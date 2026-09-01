package com.neuroassist.elastic.client;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Elasticsearch native Java client configuration.
 *
 * <p>Creates the {@link ElasticsearchClient} Spring bean used by
 * {@link com.neuroassist.elastic.adapters.ElasticsearchAdapter}.
 *
 * <p>Configuration is read from {@code application.yml}:
 * <pre>
 * elasticsearch:
 *   url: http://localhost:9200
 *   connect-timeout-ms: 2000
 *   socket-timeout-ms: 30000
 * </pre>
 *
 * <p>For multi-node clusters, set {@code elasticsearch.url} to a comma-separated
 * list of node URLs. The {@link RestClientBuilder} handles round-robin selection.
 *
 * <p>For production security (TLS + credentials), extend this configuration:
 * <ul>
 *   <li>Add {@code CredentialsProvider} with username/password, or</li>
 *   <li>Add {@code ApiKey} header via a custom {@code RequestConfigCallback}.</li>
 * </ul>
 *
 * <p>D-268: co.elastic.clients (native) — not Feign.
 */
@Configuration
public class ElasticsearchClientConfig {

    @Value("${elasticsearch.url:http://localhost:9200}")
    private String elasticsearchUrl;

    @Value("${elasticsearch.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Value("${elasticsearch.socket-timeout-ms:30000}")
    private int socketTimeoutMs;

    /**
     * Low-level REST client — manages HTTP connections and node discovery.
     * Declared as a bean so it can be closed gracefully by Spring on shutdown.
     */
    @Bean(destroyMethod = "close")
    public RestClient elasticsearchRestClient() {
        HttpHost[] hosts = parseHosts(elasticsearchUrl);
        return RestClient.builder(hosts)
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(connectTimeoutMs)
                        .setSocketTimeout(socketTimeoutMs))
                .build();
    }

    /**
     * High-level typed Java client — wraps the REST client with a Jackson mapper.
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        var transport = new RestClientTransport(restClient, new JacksonJsonpMapper(mapper));
        return new ElasticsearchClient(transport);
    }

    private static HttpHost[] parseHosts(String urlsProperty) {
        String[] urls = urlsProperty.split(",");
        HttpHost[] hosts = new HttpHost[urls.length];
        for (int i = 0; i < urls.length; i++) {
            hosts[i] = HttpHost.create(urls[i].trim());
        }
        return hosts;
    }
}

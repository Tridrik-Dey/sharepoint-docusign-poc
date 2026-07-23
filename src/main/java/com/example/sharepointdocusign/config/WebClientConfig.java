package com.example.sharepointdocusign.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * All outbound HTTP clients used by the application. Timeouts are explicit -
 * no external call may block indefinitely. Microsoft Graph clients get all
 * three of connect, read and response timeouts; the others keep the
 * connect/response pair they already had.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient microsoftIdentityWebClient(MicrosoftGraphProperties props) {
        return buildGraphWebClient(null, props);
    }

    @Bean
    public WebClient graphWebClient(MicrosoftGraphProperties props) {
        // Graph's /content endpoint responds with a redirect to a pre-authenticated
        // download URL - the client must follow it to retrieve the actual bytes.
        return buildGraphWebClient(props.graphBaseUrl(), props);
    }

    @Bean
    public WebClient docusignWebClient(DocusignProperties props) {
        return buildWebClient(props.basePath(), props.connectTimeoutMs(), props.responseTimeoutMs(), false);
    }

    @Bean
    public WebClient docusignOAuthWebClient(DocusignProperties props) {
        String baseUrl = props.oauthBasePath().startsWith("http")
                ? props.oauthBasePath()
                : "https://" + props.oauthBasePath();
        return buildWebClient(baseUrl, props.connectTimeoutMs(), props.responseTimeoutMs(), false);
    }

    /**
     * Microsoft Graph / Microsoft identity platform client: applies an explicit
     * read timeout (time allowed between bytes once the response has started)
     * in addition to the connect and overall-response timeouts, and always
     * follows redirects (needed for the Graph content-download endpoint).
     */
    private WebClient buildGraphWebClient(String baseUrl, MicrosoftGraphProperties props) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) props.connectTimeoutMs())
                .responseTimeout(Duration.ofMillis(props.responseTimeoutMs()))
                .followRedirect(true)
                .doOnConnected(connection -> connection.addHandlerLast(
                        new ReadTimeoutHandler(props.readTimeoutMs(), TimeUnit.MILLISECONDS)));

        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024));

        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    private WebClient buildWebClient(String baseUrl, long connectTimeoutMs, long responseTimeoutMs, boolean followRedirects) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs))
                .followRedirect(followRedirects);

        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024));

        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}

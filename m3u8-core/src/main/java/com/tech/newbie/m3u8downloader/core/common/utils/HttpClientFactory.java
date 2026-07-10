package com.tech.newbie.m3u8downloader.core.common.utils;

import com.tech.newbie.m3u8downloader.core.config.AppConfig;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

@Slf4j
public class HttpClientFactory {

    private HttpClientFactory() {
        // Utility class
    }

    /**
     * Creates an HttpClient with insecure SSL context that trusts all certificates.
     * Use this for downloading from servers with self-signed or invalid certificates.
     *
     * @return HttpClient configured to trust all SSL certificates
     */
    public static HttpClient createInsecureHttpClient() {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm("");  // Disable hostname verification

        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(AppConfig.getInstance().getTimeout()))
                .sslContext(getInsecureSslContext())
                .sslParameters(sslParameters)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Creates a standard HttpClient with insecure SSL context (no timeout configured).
     * Use this for simple requests where custom timeout is not needed.
     *
     * @return HttpClient configured to trust all SSL certificates
     */
    public static HttpClient createSimpleInsecureHttpClient() {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm("");  // Disable hostname verification

        return HttpClient.newBuilder()
                .sslContext(getInsecureSslContext())
                .sslParameters(sslParameters)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Creates an SSL context that trusts all certificates without verification.
     * Warning: This is insecure and should only be used when necessary.
     *
     * @return SSLContext that trusts all certificates
     */
    private static SSLContext getInsecureSslContext() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                            // Trust all clients
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                            // Trust all servers
                        }
                    }
            };
            // Use "TLS" instead of "TLSv1.2" for better compatibility
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            return sc;
        } catch (Exception e) {
            log.error("Failed to create insecure SSL context", e);
            throw new RuntimeException("Failed to create insecure SSL context", e);
        }
    }
}

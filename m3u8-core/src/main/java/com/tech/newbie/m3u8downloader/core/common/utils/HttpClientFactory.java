package com.tech.newbie.m3u8downloader.core.common.utils;

import com.tech.newbie.m3u8downloader.core.config.AppConfig;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
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
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(AppConfig.getInstance().getTimeout()))
                .sslContext(getInsecureSslContext())
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
        return HttpClient.newBuilder()
                .sslContext(getInsecureSslContext())
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
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, trustAllCerts, new SecureRandom());
            return sc;
        } catch (Exception e) {
            log.error("Failed to create insecure SSL context", e);
            throw new RuntimeException("Failed to create insecure SSL context", e);
        }
    }
}

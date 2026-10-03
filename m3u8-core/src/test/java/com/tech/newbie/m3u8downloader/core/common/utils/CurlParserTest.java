package com.tech.newbie.m3u8downloader.core.common.utils;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CurlParserTest {

    @Test
    void dropsHeadersRestrictedByHttpClient() {
        String curl = "curl 'https://example.com/a.m3u8' -H 'Connection: keep-alive' -H 'Host: example.com'"
                + " -H 'Content-Length: 0' -H 'Upgrade: h2c' -H 'Expect: 100-continue' -H 'Referer: https://example.com/'";

        CurlParser.CurlRequest req = CurlParser.parse(curl);

        assertEquals("https://example.com/a.m3u8", req.getUrl());
        assertEquals(1, req.getHeaders().size());
        assertEquals("https://example.com/", req.getHeaders().get("Referer"));
        assertFalse(req.getHeaders().containsKey("Connection"));

        // Must not throw IllegalArgumentException: restricted header name
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(req.getUrl()));
        req.getHeaders().forEach(builder::header);
        builder.build();
    }

    @Test
    void mapsCookieUserAgentAndRefererFlagsToHeaders() {
        String curl = "curl 'https://example.com/a.m3u8' -b 'sid=abc; token=xyz' -A 'UA/1.0'"
                + " -e 'https://example.com/play' -H 'accept: */*'";

        CurlParser.CurlRequest req = CurlParser.parse(curl);

        assertEquals("https://example.com/a.m3u8", req.getUrl());
        assertEquals("sid=abc; token=xyz", req.getHeaders().get("Cookie"));
        assertEquals("UA/1.0", req.getHeaders().get("User-Agent"));
        assertEquals("https://example.com/play", req.getHeaders().get("Referer"));
        assertEquals("*/*", req.getHeaders().get("accept"));
    }
}

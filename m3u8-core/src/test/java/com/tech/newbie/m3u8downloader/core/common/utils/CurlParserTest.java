package com.tech.newbie.m3u8downloader.core.common.utils;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CurlParserTest {

    private static final String URL = "https://cdn.example.com/v/index.m3u8?token=a1&exp=99";

    /** Must not throw IllegalArgumentException: restricted header name. */
    private static void assertBuildsRequest(CurlParser.CurlRequest req) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(req.getUrl()));
        req.getHeaders().forEach(builder::header);
        assertNotNull(builder.build());
    }

    @Test
    void chromePosix() {
        String curl = "curl '" + URL + "' \\\n"
                + "  -H 'accept: */*' \\\n"
                + "  -H 'accept-language: zh-TW,zh;q=0.9' \\\n"
                + "  -b $'sid=abc; note=it\\'s!' \\\n"
                + "  -H 'origin: https://www.example.com' \\\n"
                + "  -H 'referer: https://www.example.com/play/1' \\\n"
                + "  -H 'user-agent: Mozilla/5.0 Chrome/131.0.0.0'";

        CurlParser.CurlRequest req = CurlParser.parse(curl);

        assertEquals(URL, req.getUrl());
        assertEquals("sid=abc; note=it's!", req.getHeaders().get("Cookie"));
        assertEquals("https://www.example.com/play/1", req.getHeaders().get("referer"));
        assertEquals(6, req.getHeaders().size());
        assertBuildsRequest(req);
    }

    @Test
    void firefoxPosix() {
        String curl = "curl '" + URL + "' --compressed"
                + " -H 'User-Agent: Mozilla/5.0 Firefox/131.0'"
                + " -H 'Accept: */*'"
                + " -H 'Accept-Encoding: gzip, deflate, br, zstd'"
                + " -H 'Origin: https://www.example.com'"
                + " -H 'Connection: keep-alive'"
                + " -H 'Referer: https://www.example.com/play/1'"
                + " -H 'Cookie: sid=abc; theme=dark'"
                + " -H 'Sec-Fetch-Dest: empty'";

        CurlParser.CurlRequest req = CurlParser.parse(curl);

        assertEquals(URL, req.getUrl());
        assertEquals("sid=abc; theme=dark", req.getHeaders().get("Cookie"));
        assertFalse(req.getHeaders().containsKey("Connection"));
        assertFalse(req.getHeaders().containsKey("Accept-Encoding"));
        assertEquals(6, req.getHeaders().size());
        assertBuildsRequest(req);
    }

    @Test
    void chromeCmd() {
        // ^"..^" quoting, ^& / ^% escapes, %^ guard, ^ line continuation
        String curl = "curl ^\"https://cdn.example.com/v/index.m3u8?token=a1^&exp=99^\" ^\r\n"
                + "  -H ^\"accept: */*^\" ^\r\n"
                + "  -b ^\"sid=abc; q=50^%^ off; quote=^\\^\"x^\\^\"^\" ^\r\n"
                + "  -H ^\"referer: https://www.example.com/play/1^\" ^\r\n"
                + "  -H ^\"user-agent: Mozilla/5.0 Chrome/131.0.0.0^\"";

        CurlParser.CurlRequest req = CurlParser.parse(curl);

        assertEquals(URL, req.getUrl());
        assertEquals("sid=abc; q=50% off; quote=\"x\"", req.getHeaders().get("Cookie"));
        assertEquals("https://www.example.com/play/1", req.getHeaders().get("referer"));
        assertEquals(4, req.getHeaders().size());
        assertBuildsRequest(req);
    }

    @Test
    void firefoxCmd() {
        String curl = "curl ^\"https://cdn.example.com/v/index.m3u8?token=a1^&exp=99^\" --compressed"
                + " -H ^\"Accept-Encoding: gzip, deflate, br, zstd^\""
                + " -H ^\"Connection: keep-alive^\""
                + " -H ^\"Referer: https://www.example.com/play/1^\""
                + " -H ^\"Cookie: sid=abc^\"";

        CurlParser.CurlRequest req = CurlParser.parse(curl);

        assertEquals(URL, req.getUrl());
        assertEquals("sid=abc", req.getHeaders().get("Cookie"));
        assertEquals(2, req.getHeaders().size());
        assertBuildsRequest(req);
    }

    @Test
    void dropsHeadersRestrictedByHttpClient() {
        String curl = "curl 'https://example.com/a.m3u8' -H 'Connection: keep-alive' -H 'Host: example.com'"
                + " -H 'Content-Length: 0' -H 'Upgrade: h2c' -H 'Expect: 100-continue' -H 'Referer: https://example.com/'";

        CurlParser.CurlRequest req = CurlParser.parse(curl);

        assertEquals(1, req.getHeaders().size());
        assertEquals("https://example.com/", req.getHeaders().get("Referer"));
        assertBuildsRequest(req);
    }

    @Test
    void mapsUserAgentAndRefererFlagsAndSkipsFlagValues() {
        String curl = "curl -X 'GET' -A 'UA/1.0' -e 'https://example.com/play' 'https://example.com/a.m3u8'";

        CurlParser.CurlRequest req = CurlParser.parse(curl);

        assertEquals("https://example.com/a.m3u8", req.getUrl());
        assertEquals("UA/1.0", req.getHeaders().get("User-Agent"));
        assertEquals("https://example.com/play", req.getHeaders().get("Referer"));
    }
}

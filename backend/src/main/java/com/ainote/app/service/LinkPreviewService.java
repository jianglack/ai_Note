package com.ainote.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LinkPreviewService {

    private static final Logger log = LoggerFactory.getLogger(LinkPreviewService.class);
    private static final int MAX_CACHE_ENTRIES = 500;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;

    private final UrlSafetyValidator urlSafetyValidator;
    private final AddressResolver addressResolver;
    private final FixedAddressFetcher fetcher;
    private final Map<String, Map<String, String>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Map<String, String>> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            });

    private static final Pattern OG_TITLE = Pattern.compile("<meta[^>]*property=[\"']og:title[\"'][^>]*content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_DESC = Pattern.compile("<meta[^>]*property=[\"']og:description[\"'][^>]*content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_IMAGE = Pattern.compile("<meta[^>]*property=[\"']og:image[\"'][^>]*content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_SITE = Pattern.compile("<meta[^>]*property=[\"']og:site_name[\"'][^>]*content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_TAG = Pattern.compile("<title[^>]*>([^<]*)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESC_META = Pattern.compile("<meta[^>]*name=[\"']description[\"'][^>]*content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    @Autowired
    public LinkPreviewService(UrlSafetyValidator urlSafetyValidator) {
        this(urlSafetyValidator, InetAddress::getAllByName, new SocketFixedAddressFetcher());
    }

    LinkPreviewService(UrlSafetyValidator urlSafetyValidator,
                       AddressResolver addressResolver,
                       FixedAddressFetcher fetcher) {
        this.urlSafetyValidator = urlSafetyValidator;
        this.addressResolver = addressResolver;
        this.fetcher = fetcher;
    }

    public Map<String, String> fetchPreview(String url) {
        if (cache.containsKey(url)) return cache.get(url);

        Map<String, String> preview = new HashMap<>();
        preview.put("url", url);

        try {
            URI safeUri = urlSafetyValidator.requirePublicHttpUri(url);
            InetAddress fixedAddress = resolveValidatedAddress(safeUri);
            String html = fetcher.fetch(safeUri, fixedAddress);

            preview.put("title", extract(OG_TITLE, html, extract(TITLE_TAG, html, url)));
            preview.put("description", extract(OG_DESC, html, extract(DESC_META, html, "")));
            preview.put("imageUrl", extract(OG_IMAGE, html, ""));
            preview.put("siteName", extract(OG_SITE, html, safeUri.getHost()));
            preview.put("faviconUrl", "https://" + safeUri.getHost() + "/favicon.ico");

        } catch (Exception e) {
            log.warn("Failed to fetch preview for {}: {}", url, e.getMessage());
            preview.put("title", url);
            preview.put("description", "");
            preview.put("imageUrl", "");
            preview.put("siteName", "");
            preview.put("faviconUrl", "");
        }

        cache.put(url, preview);
        return preview;
    }

    private InetAddress resolveValidatedAddress(URI safeUri) throws UnknownHostException {
        InetAddress[] addresses = addressResolver.resolve(safeUri.getHost());
        for (InetAddress address : addresses) {
            try {
                URI fixedUri = new URI(
                        safeUri.getScheme(),
                        null,
                        address.getHostAddress(),
                        safeUri.getPort(),
                        "/",
                        null,
                        null
                );
                urlSafetyValidator.requirePublicHttpUri(fixedUri.toString());
                return address;
            } catch (Exception e) {
                log.debug("Rejected resolved address {} for {}: {}",
                        address.getHostAddress(), safeUri.getHost(), e.getMessage());
            }
        }
        throw new IllegalArgumentException("URL is not allowed");
    }

    private String extract(Pattern pattern, String html, String fallback) {
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1).trim() : fallback;
    }

    interface AddressResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    interface FixedAddressFetcher {
        String fetch(URI uri, InetAddress fixedAddress) throws IOException;
    }

    static class SocketFixedAddressFetcher implements FixedAddressFetcher {

        @Override
        public String fetch(URI uri, InetAddress fixedAddress) throws IOException {
            int port = effectivePort(uri);
            try (Socket socket = openSocket(uri, fixedAddress, port)) {
                socket.setSoTimeout(READ_TIMEOUT_MS);
                writeRequest(socket.getOutputStream(), uri);
                return readBody(socket.getInputStream());
            }
        }

        private Socket openSocket(URI uri, InetAddress fixedAddress, int port) throws IOException {
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if ("https".equals(scheme)) {
                Socket plain = new Socket();
                plain.connect(new InetSocketAddress(fixedAddress, port), CONNECT_TIMEOUT_MS);
                try {
                    SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                    SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(plain, uri.getHost(), port, true);
                    SSLParameters parameters = sslSocket.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    parameters.setServerNames(List.of(new SNIHostName(uri.getHost())));
                    sslSocket.setSSLParameters(parameters);
                    sslSocket.startHandshake();
                    return sslSocket;
                } catch (IOException e) {
                    plain.close();
                    throw e;
                }
            }

            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(fixedAddress, port), CONNECT_TIMEOUT_MS);
            return socket;
        }

        private void writeRequest(OutputStream outputStream, URI uri) throws IOException {
            String path = uri.getRawPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
                path += "?" + uri.getRawQuery();
            }

            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + hostHeader(uri) + "\r\n"
                    + "User-Agent: Mozilla/5.0 (compatible; AINote/1.0)\r\n"
                    + "Accept: text/html,application/xhtml+xml\r\n"
                    + "Connection: close\r\n\r\n";
            outputStream.write(request.getBytes(StandardCharsets.US_ASCII));
            outputStream.flush();
        }

        private String readBody(InputStream inputStream) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = inputStream.read(chunk)) != -1 && buffer.size() < MAX_RESPONSE_BYTES) {
                int allowed = Math.min(read, MAX_RESPONSE_BYTES - buffer.size());
                buffer.write(chunk, 0, allowed);
            }

            String response = buffer.toString(StandardCharsets.UTF_8);
            int separator = response.indexOf("\r\n\r\n");
            int separatorLength = 4;
            if (separator < 0) {
                separator = response.indexOf("\n\n");
                separatorLength = 2;
            }
            if (separator < 0) {
                return response;
            }

            String headers = response.substring(0, separator).toLowerCase(Locale.ROOT);
            String body = response.substring(separator + separatorLength);
            if (headers.contains("transfer-encoding: chunked")) {
                return decodeChunkedBody(body);
            }
            return body;
        }

        private String decodeChunkedBody(String body) {
            StringBuilder decoded = new StringBuilder();
            int offset = 0;
            while (offset < body.length()) {
                int lineEnd = body.indexOf("\r\n", offset);
                if (lineEnd < 0) {
                    break;
                }
                String sizeText = body.substring(offset, lineEnd).split(";", 2)[0].trim();
                int size;
                try {
                    size = Integer.parseInt(sizeText, 16);
                } catch (NumberFormatException e) {
                    return body;
                }
                if (size == 0) {
                    break;
                }

                int dataStart = lineEnd + 2;
                int dataEnd = Math.min(dataStart + size, body.length());
                decoded.append(body, dataStart, dataEnd);
                offset = dataEnd + 2;
            }
            return decoded.toString();
        }

        private static int effectivePort(URI uri) {
            if (uri.getPort() > 0) {
                return uri.getPort();
            }
            return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }

        private static String hostHeader(URI uri) {
            String host = uri.getHost();
            if (host.contains(":") && !host.startsWith("[")) {
                host = "[" + host + "]";
            }
            int port = uri.getPort();
            if (port > 0 && port != effectivePort(uri)) {
                return host + ":" + port;
            }
            return host;
        }
    }
}

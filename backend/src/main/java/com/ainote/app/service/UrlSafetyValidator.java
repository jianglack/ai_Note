package com.ainote.app.service;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

@Component
public class UrlSafetyValidator {

    public URI requirePublicHttpUri(String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL is not allowed", e);
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Only http and https URLs are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL is not allowed");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")) {
            throw new IllegalArgumentException("URL is not allowed");
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!isPublicAddress(address)) {
                    throw new IllegalArgumentException("URL is not allowed");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("URL is not allowed", e);
        }

        return uri;
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        if (address instanceof Inet4Address) {
            return isPublicIpv4(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            return isPublicIpv6(address.getAddress());
        }
        return false;
    }

    private boolean isPublicIpv4(byte[] raw) {
        int first = raw[0] & 0xff;
        int second = raw[1] & 0xff;

        if (first == 0 || first == 10 || first == 127) {
            return false;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return false;
        }
        if (first == 169 && second == 254) {
            return false;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return false;
        }
        if (first == 192 && (second == 0 || second == 168)) {
            return false;
        }
        if (first == 198 && (second == 18 || second == 19)) {
            return false;
        }
        return first < 224;
    }

    private boolean isPublicIpv6(byte[] raw) {
        int first = raw[0] & 0xff;
        if ((first & 0xfe) == 0xfc) {
            return false;
        }
        return true;
    }
}

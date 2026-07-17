package com.example.aichat.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * 网络安全工具类。
 * 用于防止 SSRF 攻击：验证用户配置的 API URL 不指向内网或被禁用的地址。
 */
public final class NetworkUtils {

    private NetworkUtils() {}

    /**
     * 验证 URL 是否安全可访问。
     * 拒绝内网地址、保留地址和 localhost。
     *
     * @param url 待验证的 URL
     * @throws IllegalArgumentException 如果 URL 指向内网或被禁用
     */
    public static void validateExternalUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("API URL 不能为空");
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的 API URL 格式: " + url);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"https".equals(scheme) && !"http".equals(scheme))) {
            throw new IllegalArgumentException("API URL 仅支持 http/https 协议");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("API URL 缺少主机名");
        }

        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无法解析 API URL 主机: " + host);
        }

        if (addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isAnyLocalAddress()) {
            throw new IllegalArgumentException("禁止访问内网或本地地址: " + host);
        }

        // 额外检查常见内网 IP 段 (isSiteLocalAddress 不覆盖所有情况)
        byte[] octets = addr.getAddress();
        if (octets.length == 4) {
            // 0.0.0.0/8
            if (octets[0] == 0) throw new IllegalArgumentException("禁止访问保留地址: " + host);
            // 127.0.0.0/8 (loopback)
            if (octets[0] == 127) throw new IllegalArgumentException("禁止访问本地回环地址: " + host);
        }
    }
}

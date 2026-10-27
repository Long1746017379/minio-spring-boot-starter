package com.along.boot.minio.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MinIO 内容哈希计算工具。
 * <p>提供 MD5 哈希计算，用于秒传（内容去重）场景。</p>
 *
 * @author along
 */
public class MinioHashUtil {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * 计算输入流的 MD5 哈希值（32 位小写十六进制）。
     * <p>注意：此方法会消费输入流，调用前请确保流可读。</p>
     *
     * @param stream 输入流
     * @return MD5 十六进制小写字符串
     */
    public static String computeMD5(InputStream stream) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            DigestInputStream dis = new DigestInputStream(stream, md);
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
                // digest is updated automatically by DigestInputStream
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read stream for MD5 computation", e);
        }
    }

    /**
     * 计算字节数组的 MD5 哈希值。
     *
     * @param data 字节数组
     * @return MD5 十六进制小写字符串
     */
    public static String computeMD5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * 将摘要字节数组转换为小写十六进制字符串。
     *
     * @param bytes 摘要字节数组
     * @return 小写十六进制字符串
     */
    public static String toHex(byte[] bytes) {
        return bytesToHex(bytes);
    }

    /**
     * 将字节数组转换为小写十六进制字符串。
     */
    private static String bytesToHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = HEX_CHARS[v >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hex);
    }

}

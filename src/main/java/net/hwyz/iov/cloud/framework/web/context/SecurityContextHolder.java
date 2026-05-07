package net.hwyz.iov.cloud.framework.web.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import jakarta.servlet.http.HttpServletRequest;
import net.hwyz.iov.cloud.framework.common.constant.CustomHeaders;
import net.hwyz.iov.cloud.framework.common.constant.SecurityConstants;
import net.hwyz.iov.cloud.framework.common.constant.TokenConstants;
import net.hwyz.iov.cloud.framework.common.util.Convert;
import net.hwyz.iov.cloud.framework.common.util.ServletUtil;
import net.hwyz.iov.cloud.framework.common.util.StrUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 获取当前线程变量中的 用户id、用户名称、Token等信息
 * 注意： 必须在网关通过请求头的方法传入，同时在HeaderInterceptor拦截器设置值。 否则这里无法获取
 *
 * @author hwyz_leo
 */
public class SecurityContextHolder {
    private static final TransmittableThreadLocal<Map<String, Object>> THREAD_LOCAL = new TransmittableThreadLocal<>();

    public static void set(String key, Object value) {
        Map<String, Object> map = getLocalMap();
        map.put(key, value == null ? StrUtil.EMPTY : value);
    }

    public static String get(String key) {
        Map<String, Object> map = getLocalMap();
        return Convert.toStr(map.getOrDefault(key, StrUtil.EMPTY));
    }

    public static <T> T get(String key, Class<T> clazz) {
        Map<String, Object> map = getLocalMap();
        return StrUtil.cast(map.getOrDefault(key, null));
    }

    public static Map<String, Object> getLocalMap() {
        Map<String, Object> map = THREAD_LOCAL.get();
        if (map == null) {
            map = new ConcurrentHashMap<String, Object>();
            THREAD_LOCAL.set(map);
        }
        return map;
    }

    public static void setLocalMap(Map<String, Object> threadLocalMap) {
        THREAD_LOCAL.set(threadLocalMap);
    }

    public static String getUserId() {
        return get(SecurityConstants.USER_ID);
    }

    /**
     * 获取用户ID（Long类型）
     */
    public static Long getUserIdAsLong() {
        String userId = getUserId();
        return StrUtil.isNotEmpty(userId) ? Long.valueOf(userId) : null;
    }

    public static void setUserId(String account) {
        set(SecurityConstants.USER_ID, account);
    }

    public static String getUserName() {
        return get(SecurityConstants.USERNAME);
    }

    public static void setUserName(String username) {
        set(SecurityConstants.USERNAME, username);
    }

    public static String getUserKey() {
        return get(SecurityConstants.USER_KEY);
    }

    public static void setUserKey(String userKey) {
        set(SecurityConstants.USER_KEY, userKey);
    }

    public static String getClientId() {
        return get(SecurityConstants.CLIENT_ID);
    }

    public static void setClientId(String clientId) {
        set(SecurityConstants.CLIENT_ID, clientId);
    }

    public static String getClientType() {
        return get(SecurityConstants.CLIENT_TYPE);
    }

    public static void setClientType(String clientType) {
        set(SecurityConstants.CLIENT_TYPE, clientType);
    }

    public static String getDeviceId() {
        return get(SecurityConstants.DEVICE_ID);
    }

    public static void setDeviceId(String deviceId) {
        set(SecurityConstants.DEVICE_ID, deviceId);
    }

    public static String getPlatform() {
        return get(SecurityConstants.PLATFORM);
    }

    public static void setPlatform(String platform) {
        set(SecurityConstants.PLATFORM, platform);
    }

    public static String getOsVersion() {
        return get(SecurityConstants.OS_VERSION);
    }

    public static void setOsVersion(String osVersion) {
        set(SecurityConstants.OS_VERSION, osVersion);
    }

    public static String getAppVersion() {
        return get(SecurityConstants.APP_VERSION);
    }

    public static void setAppVersion(String appVersion) {
        set(SecurityConstants.APP_VERSION, appVersion);
    }

    public static String getSessionId() {
        return get(SecurityConstants.SESSION_ID);
    }

    public static void setSessionId(String sessionId) {
        set(SecurityConstants.SESSION_ID, sessionId);
    }

    public static String getScope() {
        return get(SecurityConstants.SCOPE);
    }

    public static void setScope(String scope) {
        set(SecurityConstants.SCOPE, scope);
    }

    public static String getPermission() {
        return get(SecurityConstants.ROLE_PERMISSION);
    }

    public static void setPermission(String permissions) {
        set(SecurityConstants.ROLE_PERMISSION, permissions);
    }

    /**
     * 获取请求token
     */
    public static String getToken() {
        return getToken(ServletUtil.getRequest());
    }

    /**
     * 根据request获取请求token
     */
    public static String getToken(HttpServletRequest request) {
        // 从header获取token标识
        String token = request.getHeader(CustomHeaders.AUTHORIZATION_HEADER);
        return replaceTokenPrefix(token);
    }

    /**
     * 裁剪token前缀
     */
    public static String replaceTokenPrefix(String token) {
        // 如果前端设置了令牌前缀，则裁剪掉前缀
        if (StrUtil.isNotEmpty(token) && token.startsWith(TokenConstants.PREFIX)) {
            token = token.replaceFirst(TokenConstants.PREFIX, "");
        }
        return token;
    }

    public static void remove() {
        THREAD_LOCAL.remove();
    }
}

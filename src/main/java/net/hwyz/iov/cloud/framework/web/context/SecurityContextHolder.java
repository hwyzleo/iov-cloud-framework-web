package net.hwyz.iov.cloud.framework.web.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import net.hwyz.iov.cloud.framework.common.util.Convert;
import net.hwyz.iov.cloud.framework.common.util.StrUtil;
import net.hwyz.iov.cloud.framework.web.constant.MptSecurityConstants;

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

    public static Long getUserId() {
        return Convert.toLong(get(MptSecurityConstants.DETAILS_USER_ID), 0L);
    }

    public static void setUserId(String account) {
        set(MptSecurityConstants.DETAILS_USER_ID, account);
    }

    public static String getUserName() {
        return get(MptSecurityConstants.DETAILS_USERNAME);
    }

    public static void setUserName(String username) {
        set(MptSecurityConstants.DETAILS_USERNAME, username);
    }

    public static String getUserKey() {
        return get(MptSecurityConstants.USER_KEY);
    }

    public static void setUserKey(String userKey) {
        set(MptSecurityConstants.USER_KEY, userKey);
    }

    public static String getPermission() {
        return get(MptSecurityConstants.ROLE_PERMISSION);
    }

    public static void setPermission(String permissions) {
        set(MptSecurityConstants.ROLE_PERMISSION, permissions);
    }

    public static void remove() {
        THREAD_LOCAL.remove();
    }
}

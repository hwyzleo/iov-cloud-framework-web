package net.hwyz.iov.cloud.framework.web.enums;

import lombok.AllArgsConstructor;

/**
 * 自定义Headers
 *
 * @author hwyz_leo
 */
@AllArgsConstructor
public enum CustomHeaders {

    /** 客户端ID **/
    CLIENT_ID("clientId"),
    /** 客户端类型 **/
    CLIENT_TYPE("clientType"),
    /** 客户端账号 **/
    CLIENT_ACCOUNT("clientAccount"),
    /** 令牌 **/
    TOKEN("token"),
    /** 车架号 **/
    VIN("vin");

    public final String value;

}

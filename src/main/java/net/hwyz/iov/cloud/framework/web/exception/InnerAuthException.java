package net.hwyz.iov.cloud.framework.web.exception;

import net.hwyz.iov.cloud.framework.common.exception.BaseException;

/**
 * 内部认证异常
 *
 * @author hwyz_leo
 */
public class InnerAuthException extends BaseException {

    public InnerAuthException(String message) {
        super(message);
    }
}

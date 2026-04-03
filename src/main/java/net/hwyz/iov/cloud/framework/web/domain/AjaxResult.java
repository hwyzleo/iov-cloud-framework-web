package net.hwyz.iov.cloud.framework.web.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.hwyz.iov.cloud.framework.web.constant.HttpStatus;

import java.io.Serial;
import java.io.Serializable;

/**
 * RuoYi 兼容性操作消息提醒（POJO 版）
 *
 * @author hwyz_leo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AjaxResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 状态码 (RuoYi 后台识别 int 类型)
     */
    private int code;

    /**
     * 返回内容
     */
    private String msg;

    /**
     * 数据对象
     */
    private T data;

    /**
     * 返回成功消息
     */
    public static <T> AjaxResult<T> success() {
        return AjaxResult.success("操作成功");
    }

    /**
     * 返回成功数据
     */
    public static <T> AjaxResult<T> success(T data) {
        return AjaxResult.success("操作成功", data);
    }

    /**
     * 返回成功消息
     */
    public static <T> AjaxResult<T> success(String msg) {
        return AjaxResult.success(msg, null);
    }

    /**
     * 返回成功消息
     */
    public static <T> AjaxResult<T> success(String msg, T data) {
        return AjaxResult.<T>builder()
                .code(HttpStatus.SUCCESS)
                .msg(msg)
                .data(data)
                .build();
    }

    /**
     * 返回错误消息
     */
    public static <T> AjaxResult<T> error() {
        return AjaxResult.error("操作失败");
    }

    /**
     * 返回错误消息
     */
    public static <T> AjaxResult<T> error(String msg) {
        return AjaxResult.error(msg, null);
    }

    /**
     * 返回错误消息
     */
    public static <T> AjaxResult<T> error(String msg, T data) {
        return AjaxResult.<T>builder()
                .code(HttpStatus.ERROR)
                .msg(msg)
                .data(data)
                .build();
    }

    /**
     * 返回错误消息
     */
    public static <T> AjaxResult<T> error(int code, String msg) {
        return AjaxResult.<T>builder()
                .code(code)
                .msg(msg)
                .build();
    }

    /**
     * 是否为成功消息
     */
    public boolean isSuccess() {
        return HttpStatus.SUCCESS == this.code;
    }
}

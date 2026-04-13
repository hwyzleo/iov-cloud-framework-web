package net.hwyz.iov.cloud.framework.web.controller;

import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.framework.common.bean.AjaxResult;
import net.hwyz.iov.cloud.framework.common.bean.BaseRequest;
import net.hwyz.iov.cloud.framework.common.constant.HttpStatus;
import net.hwyz.iov.cloud.framework.common.util.DateUtil;
import net.hwyz.iov.cloud.framework.web.page.TableDataInfo;
import net.hwyz.iov.cloud.framework.web.util.PageUtil;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;

import java.beans.PropertyEditorSupport;
import java.util.Date;
import java.util.List;

/**
 * web层通用数据处理
 *
 * @author hwyz_leo
 */
@Slf4j
public class BaseController {

    /**
     * 将前台传递过来的日期格式的字符串，自动转化为Date类型
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        // Date 类型转换
        binder.registerCustomEditor(Date.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(DateUtil.parseDate(text));
            }
        });
    }

    /**
     * 获取请求参数中的开始时间
     *
     * @param request 请求
     * @return 开始时间
     */
    protected Date getBeginTime(BaseRequest request) {
        if (request == null || request.getParams() == null || !request.getParams().containsKey("beginTime")) {
            return null;
        }
        try {
            return DateUtil.parseDate(request.getParams().get("beginTime").toString());
        } catch (Exception e) {
            log.warn("日期转换异常:{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取请求参数中的结束时间
     *
     * @param request 请求
     * @return 结束时间
     */
    protected Date getEndTime(BaseRequest request) {
        if (request == null || request.getParams() == null || !request.getParams().containsKey("endTime")) {
            return null;
        }
        try {
            return DateUtil.parseDate(request.getParams().get("endTime").toString());
        } catch (Exception e) {
            log.warn("日期转换异常:{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 设置请求分页数据
     */
    protected void startPage() {
        PageUtil.startPage();
    }

    /**
     * 清理分页的线程变量
     */
    protected void clearPage() {
        PageUtil.clearPage();
    }

    /**
     * 响应请求分页数据
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected TableDataInfo getDataTable(List<?> list) {
        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setRows(list);
        rspData.setMsg("查询成功");
        rspData.setTotal(new PageInfo(list).getTotal());
        return rspData;
    }

    /**
     * 响应请求分页数据
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected TableDataInfo getDataTable(List<?> rawlist, List<?> list) {
        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setRows(list);
        rspData.setMsg("查询成功");
        rspData.setTotal(new PageInfo(rawlist).getTotal());
        return rspData;
    }

    /**
     * 响应请求分页数据
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected TableDataInfo getDataTable(List<?> list, long total) {
        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setRows(list);
        rspData.setMsg("查询成功");
        rspData.setTotal(total);
        return rspData;
    }

    /**
     * 返回成功
     */
    public AjaxResult success() {
        return AjaxResult.success();
    }

    /**
     * 返回成功消息
     */
    public AjaxResult success(String message) {
        return AjaxResult.success(message);
    }

    /**
     * 返回成功消息
     */
    public AjaxResult success(Object data) {
        return AjaxResult.success(data);
    }

    /**
     * 返回失败消息
     */
    public AjaxResult error() {
        return AjaxResult.error();
    }

    /**
     * 返回失败消息
     */
    public AjaxResult error(String message) {
        return AjaxResult.error(message);
    }

    /**
     * 返回警告消息
     */
    public AjaxResult warn(String message) {
        return AjaxResult.error(message);
    }

    /**
     * 响应返回结果
     *
     * @param rows 影响行数
     * @return 操作结果
     */
    protected AjaxResult toAjax(int rows) {
        return rows > 0 ? AjaxResult.success() : AjaxResult.error();
    }

    /**
     * 响应返回结果
     *
     * @param result 结果
     * @return 操作结果
     */
    protected AjaxResult toAjax(boolean result) {
        return result ? success() : error();
    }
}

package net.hwyz.iov.cloud.framework.web.util;

import com.github.pagehelper.PageHelper;
import net.hwyz.iov.cloud.framework.common.util.SqlUtil;
import net.hwyz.iov.cloud.framework.web.page.PageDomain;
import net.hwyz.iov.cloud.framework.web.page.TableSupport;

/**
 * 分页工具类
 *
 * @author hwyz_leo
 */
public class PageUtil extends PageHelper {
    /**
     * 设置请求分页数据
     */
    public static void startPage() {
        PageDomain pageDomain = TableSupport.buildPageRequest();
        Integer pageNum = pageDomain.getPageNum();
        Integer pageSize = pageDomain.getPageSize();
        String orderBy = SqlUtil.escapeOrderBySql(pageDomain.getOrderBy());
        Boolean reasonable = pageDomain.getReasonable();
        PageHelper.startPage(pageNum, pageSize, orderBy).setReasonable(reasonable);
    }

    /**
     * 清理分页的线程变量
     */
    public static void clearPage() {
        PageHelper.clearPage();
    }
}

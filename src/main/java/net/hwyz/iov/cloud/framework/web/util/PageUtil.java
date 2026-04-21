package net.hwyz.iov.cloud.framework.web.util;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import net.hwyz.iov.cloud.framework.common.bean.PageResult;
import net.hwyz.iov.cloud.framework.common.util.SqlUtil;
import net.hwyz.iov.cloud.framework.web.page.PageDomain;
import net.hwyz.iov.cloud.framework.web.page.TableSupport;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /**
     * 响应请求分页数据
     */
    public static <T> PageResult<T> getPageResult(List<T> list) {
        PageResult<T> result = new PageResult<>();
        result.setItems(list);
        if (list instanceof com.github.pagehelper.Page) {
            result.setTotal(((com.github.pagehelper.Page) list).getTotal());
        } else {
            result.setTotal(list.size());
        }
        return result;
    }

    /**
     * 将包含分页信息的列表转换为另一种类型的分页列表。
     *
     * @param originalList 原始分页列表
     * @param mapper       转换函数
     * @param <S>          原类型
     * @param <T>          目标类型
     * @return 转换后且保留分页信息的列表
     */
    public static <S, T> List<T> convert(List<S> originalList, Function<S, T> mapper) {
        List<T> targetList = originalList.stream()
                .map(mapper)
                .collect(Collectors.toList());

        if (originalList instanceof Page) {
            Page<S> page = (Page<S>) originalList;
            Page<T> newPage = new Page<>(page.getPageNum(), page.getPageSize(), page.isCount());
            newPage.setTotal(page.getTotal());
            newPage.addAll(targetList);
            return newPage;
        }

        return targetList;
    }
}

package com.hollow.build.fund;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 基金监控模块 MyBatis Mapper。
 */
@Mapper
public interface FundMapper {

    /**
     * 按基金代码查询基金（含已停用的）。
     *
     * @param fundCode 基金代码
     * @return 基金，未找到返回 null
     */
    Fund selectByCode(@Param("fundCode") String fundCode);

    /**
     * 查询所有启用监控（enabled=1）的基金。
     *
     * @return 监控中的基金列表
     */
    List<Fund> selectEnabledList();

    /**
     * 新增或更新基金（按 fund_code 唯一键）。
     * <p>已存在时更新名称/类型并重新启用监控，不覆盖 latest_nav_date。</p>
     *
     * @param fund 基金信息
     */
    void insertOrUpdateFund(Fund fund);

    /**
     * 更新某基金已入库的最新净值日期。
     *
     * @param fundCode 基金代码
     * @param navDate  最新净值日期
     */
    void updateLatestNavDate(@Param("fundCode") String fundCode, @Param("navDate") LocalDate navDate);

    /**
     * 停用某基金的监控（enabled=0），保留历史净值。
     *
     * @param fundCode 基金代码
     */
    void disableFund(@Param("fundCode") String fundCode);

    /**
     * 批量插入或更新净值（按 fund_code + nav_date 唯一键）。
     * <p>已存在的净值会被覆盖，支持基金公司事后修正净值的场景。</p>
     *
     * @param list 净值列表
     */
    void batchUpsertNav(@Param("list") List<FundNav> list);

    /**
     * 查询某基金从指定日期起（含）的净值，按净值日期升序。
     *
     * @param fundCode  基金代码
     * @param startDate 起始净值日期（含）
     * @return 净值列表（升序）
     */
    List<FundNav> selectNavByCodeAndRange(@Param("fundCode") String fundCode, @Param("startDate") LocalDate startDate);

    /**
     * 查询某基金最新一条净值。
     *
     * @param fundCode 基金代码
     * @return 最新净值，无数据返回 null
     */
    FundNav selectLatestNav(@Param("fundCode") String fundCode);

    /**
     * 查询某基金库内已入库的最大净值日期。
     *
     * @param fundCode 基金代码
     * @return 最大净值日期，无数据返回 null
     */
    LocalDate selectMaxNavDate(@Param("fundCode") String fundCode);

    /**
     * 新增或更新基金画像快照（按 fund_code 主键覆盖）。
     *
     * @param profile 画像快照
     */
    void upsertProfile(FundProfile profile);

    /**
     * 查询基金画像快照。
     *
     * @param fundCode 基金代码
     * @return 画像快照，无数据返回 null
     */
    FundProfile selectProfileByCode(@Param("fundCode") String fundCode);

    /**
     * 批量新增或更新阶段收益排名（按 fund_code + period_code 覆盖）。
     *
     * @param list 阶段收益排名列表
     */
    void batchUpsertPeriodRank(@Param("list") List<FundPeriodRank> list);

    /**
     * 查询某基金阶段收益排名，按常用点评顺序返回。
     *
     * @param fundCode 基金代码
     * @return 阶段收益排名列表
     */
    List<FundPeriodRank> selectPeriodRanksByCode(@Param("fundCode") String fundCode);
}

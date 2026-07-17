package com.airadar.scoring.mapper;

import com.airadar.scoring.entity.HotScoreEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HotScoreMapper extends BaseMapper<HotScoreEntity> {

    /**
     * Lists all score records for a cluster, newest first.
     *
     * <p>Used by the comparison API to return every scoring version side by side.
     *
     * @param clusterId the hot cluster id
     * @return score records ordered by {@code calculated_at} descending
     */
    @Select("SELECT * FROM hot_score WHERE hot_cluster_id = #{clusterId} ORDER BY calculated_at DESC")
    List<HotScoreEntity> selectByCluster(@Param("clusterId") Long clusterId);

    /**
     * Finds the most recent score for a cluster under a specific scoring version.
     *
     * @param clusterId the hot cluster id
     * @param version   the scoring version tag
     * @return the latest score entity, or {@code null} if none exists
     */
    @Select("SELECT * FROM hot_score WHERE hot_cluster_id = #{clusterId} "
            + "AND scoring_version = #{version} ORDER BY calculated_at DESC LIMIT 1")
    HotScoreEntity selectLatestByClusterAndVersion(@Param("clusterId") Long clusterId,
                                                    @Param("version") String version);
}

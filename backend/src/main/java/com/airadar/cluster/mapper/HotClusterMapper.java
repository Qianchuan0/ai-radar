package com.airadar.cluster.mapper;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.source.model.SourceType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface HotClusterMapper extends BaseMapper<HotClusterEntity> {

    List<HotClusterEntity> selectActivePage(
            @Param("sourceType") SourceType sourceType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("sort") String sort,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    long countActive(
            @Param("sourceType") SourceType sourceType,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}

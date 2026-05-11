package com.hollow.build.agent.repository;

import com.hollow.build.agent.entity.AgentRequestLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentRequestLogMapper {

    /** 插入主表，回写自增主键到 entity.id */
    void insert(AgentRequestLog log);
}

package org.ticketing_system.biz.aiservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.ticketing_system.biz.aiservice.dao.entity.AiEpisodeDO;

import java.util.List;

/**
 * AI 情节 Mapper
 */
public interface AiEpisodeMapper extends BaseMapper<AiEpisodeDO> {

    @Select("SELECT * FROM t_ai_episode WHERE user_id = #{userId} AND del_flag = 0 ORDER BY create_time DESC LIMIT #{limit}")
    List<AiEpisodeDO> selectByUserId(@Param("userId") Long userId, @Param("limit") int limit);
}

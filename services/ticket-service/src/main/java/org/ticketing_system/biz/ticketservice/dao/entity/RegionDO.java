package org.ticketing_system.biz.ticketservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.ticketing_system.framework.starter.database.base.BaseDO;

/**
 * 地区表
 * @author lin667z
 */
@Data
@TableName("t_region")
public class RegionDO extends BaseDO {

    /**
     * id
     */
    private Long id;

    /**
     * 地区名称
     */
    private String name;

    /**
     * 地区全名
     */
    private String fullName;

    /**
     * 地区编码
     */
    private String code;

    /**
     * 地区首字母
     */
    private String initial;

    /**
     * 拼音
     */
    private String spell;

    /**
     * 热门标识
     */
    private Integer popularFlag;
}



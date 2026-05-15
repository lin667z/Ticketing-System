package org.ticketing_system.framework.starter.database.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.ticketing_system.framework.starter.common.enums.DelEnum;

import java.util.Date;

/**
 * 元数据处理器
 * @author lin667z
 */
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 数据新增时填充
     *
     * @param metaObject
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", Date.class, new Date());
        this.strictInsertFill(metaObject, "updateTime", Date.class, new Date());
        this.strictInsertFill(metaObject, "delFlag", Integer.class, DelEnum.NORMAL.code());
    }

    /**
     * 数据修改时填充
     *
     * @param metaObject
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "updateTime", Date.class, new Date());
    }
}



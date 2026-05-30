package org.ticketing_system.biz.aiservice.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;

/**
 * 会话响应
 */
@Data
public class ConversationRespDTO {

  /**
   * 会话 ID
   */
  @JsonSerialize(using = ToStringSerializer.class)
  private Long id;

  /**
   * 会话标题
   */
  private String title;

  /**
   * 状态 0：进行中 1：已结束
   */
  private Integer status;

  /**
   * 消息数量
   */
  private Long messageCount;

  /**
   * 创建时间
   */
  private Date createTime;

  /**
   * 更新时间
   */
  private Date updateTime;
}

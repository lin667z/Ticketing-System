package org.ticketing_system.biz.aiservice.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 对话详情
 */
@Data
public class ConversationDetailRespDTO {

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
   * 创建时间
   */
  private Date createTime;

  /**
   * 更新时间
   */
  private Date updateTime;

  /**
   * 消息列表
   */
  private List<MessageRespDTO> messages;

  /**
   * 消息详情
   */
  @Data
  public static class MessageRespDTO {

    /**
     * 消息 ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 角色 (user, assistant, system, tool)
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * Token 消耗
     */
    private Integer tokenCount;

    /**
     * 创建时间
     */
    private Date createTime;
  }
}

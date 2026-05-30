package org.ticketing_system.biz.aiservice.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重命名对话请求
 */
@Data
public class RenameConversationReqDTO {

  /**
   * 新名称
   */
  @NotBlank(message = "对话名称不能为空")
  private String newName;
}

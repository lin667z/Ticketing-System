package org.ticketing_system.biz.aiservice.service;

import org.ticketing_system.biz.aiservice.dto.resp.ConversationDetailRespDTO;
import org.ticketing_system.biz.aiservice.dto.resp.ConversationRespDTO;

import java.util.List;

/**
 * 智能客服对话管理服务接口
 */
public interface ConversationService {

  /**
   * 创建新对话
   */
  ConversationRespDTO createConversation(Long userId);

  /**
   * 删除对话（软删除）
   */
  void deleteConversation(Long conversationId, Long userId);

  /**
   * 重命名对话
   */
  ConversationRespDTO renameConversation(Long conversationId, Long userId, String newName);

  /**
   * 获取用户的对话列表
   */
  List<ConversationRespDTO> listConversations(Long userId);

  /**
   * 获取对话详情及消息历史
   */
  ConversationDetailRespDTO getConversationDetail(Long conversationId, Long userId);
}

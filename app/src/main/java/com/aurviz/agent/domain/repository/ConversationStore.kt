package com.aurviz.agent.domain.repository

import com.aurviz.agent.domain.model.AgentMessage

interface ConversationStore {
    fun loadMessages(): List<AgentMessage>
    fun saveMessages(messages: List<AgentMessage>)
    fun clear()
}

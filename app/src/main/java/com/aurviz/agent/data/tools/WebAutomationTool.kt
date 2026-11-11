package com.aurviz.agent.data.tools

import com.aurviz.agent.domain.model.ToolExecutionResponse
import com.aurviz.agent.domain.tool.AgentTool
import com.aurviz.agent.service.web.ControlledWebScraper

class WebAutomationTool(
    private val webScraper: ControlledWebScraper
) : AgentTool {

    override val name: String = "browse_web"
    override val description: String = "Browse a website, navigate pages, and extract clean text or headlines."

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        val url = arguments["url"] as? String
            ?: return ToolExecutionResponse(name, false, "Missing 'url' parameter.")
        val action = arguments["action"] as? String ?: "extract_summary"

        val extracted = webScraper.browseAndExtract(url, action)
        return ToolExecutionResponse(
            toolName = name,
            isSuccess = true,
            output = extracted
        )
    }
}

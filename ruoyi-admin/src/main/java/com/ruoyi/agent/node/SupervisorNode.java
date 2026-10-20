package com.ruoyi.agent.node;

import com.ruoyi.agent.dto.AgentResponse;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SupervisorNode {

    private static final Logger log = LoggerFactory.getLogger(SupervisorNode.class);

    private final ChatLanguageModel routerModel;

    public SupervisorNode() {
        this.routerModel = OpenAiChatModel.builder()
                .apiKey("")
                .baseUrl("https://api.deepseek.com/v1")
                .modelName("deepseek-chat")
                .temperature(0.0)
                .build();
    }

    public String route(String userMessage, String conversationContext) {
        String systemPrompt = buildRouterSystemPrompt();
        
        String fullPrompt = systemPrompt + "\n\n=== 对话历史 ===\n" + 
                           conversationContext + 
                           "\n\n=== 当前用户消息 ===\n" + 
                           userMessage +
                           "\n\n请只输出一个词：RESEARCH、K8S、SYSTEM、ALERT、CHAT 或 FINISH";
        
        try {
            log.info("[主管路由] 开始调用LLM进行路由决策");
            String response = routerModel.generate(fullPrompt);
            
            if (response == null || response.trim().isEmpty()) {
                log.warn("[主管路由] LLM返回为空，降级到CHAT");
                return "CHAT";
            }
            
            response = response.trim().toUpperCase();
            log.info("[主管路由] LLM返回: {}", response);
            
            if (response.contains("K8S")) {
                return "K8S";
            } else if (response.contains("SYSTEM")) {
                return "SYSTEM";
            } else if (response.contains("ALERT")) {
                return "ALERT";
            } else if (response.contains("RESEARCH")) {
                return "K8S";
            } else if (response.contains("FINISH")) {
                return "FINISH";
            } else {
                return "CHAT";
            }
            
        } catch (Exception e) {
            log.error("[主管路由] 调用LLM异常，降级到CHAT", e);
            return "CHAT";
        }
    }

    public AgentResponse handleChat(String userMessage) {
        try {
            log.info("[主管闲聊] 开始处理用户消息: {}", userMessage);
            
            String systemPrompt = "你是运维智能助手团队的主管。请用友好、简洁的语言回应用户的问候或闲聊。不要使用Markdown格式。";
            
            log.info("[主管闲聊] 正在调用LLM...");
            String reply = routerModel.generate(systemPrompt + "\n\n用户: " + userMessage);
            
            log.info("[主管闲聊] LLM返回结果: {}", reply);
            
            if (reply == null || reply.trim().isEmpty()) {
                log.warn("[主管闲聊] LLM返回为空，使用默认回复");
                reply = "您好！我是运维智能助手，请问有什么可以帮助您的吗？";
            }
            
            AgentResponse response = new AgentResponse();
            response.setReply(reply);
            response.setType("text");
            response.setAgent("supervisor");
            
            log.info("[主管闲聊] 准备返回响应: reply={}, type={}", response.getReply(), response.getType());
            return response;
        } catch (Exception e) {
            log.error("[主管闲聊] 调用LLM异常", e);
            AgentResponse response = new AgentResponse();
            response.setReply("您好！我是运维智能助手，请问有什么可以帮助您的吗？");
            response.setType("text");
            response.setAgent("supervisor");
            return response;
        }
    }

    public AgentResponse handleFinish(String userMessage) {
        AgentResponse response = new AgentResponse();
        response.setReply("好的，如果您还有其他问题，随时可以问我。祝您工作顺利！😊");
        response.setType("text");
        return response;
    }

    public AgentResponse handleDefault(String userMessage) {
        AgentResponse response = new AgentResponse();
        response.setReply("抱歉，我没有理解您的需求。您可以尝试：\n" +
                         "- 查询K8s资源状态\n" +
                         "- 查看系统监控信息\n" +
                         "- 管理告警规则\n\n" +
                         "请更详细地描述您的问题。");
        response.setType("text");
        return response;
    }

    private String buildRouterSystemPrompt() {
        return "你是一个运维智能助手团队的主管，负责分析用户需求并分发给合适的专业智能体。\n\n" +
               "【智能体分工】\n" +
               "- K8S：Kubernetes相关问题（Pod、Service、Deployment、日志、排障等）\n" +
               "- SYSTEM：物理设备相关（服务器、数据库、中间件、网络设备等）\n" +
               "- ALERT：告警相关（告警规则、告警历史、告警分析、告警配置）\n" +
               "- CHAT：日常问候、闲聊、不需要工具的简单问题\n" +
               "- FINISH：任务已完成，等待用户新问题\n\n" +
               "【判断规则】\n" +
               "1. 包含k8s、kubernetes、pod、deployment、service、namespace等关键词 → K8S\n" +
               "2. 包含服务器、数据库、mysql、redis、物理机、主机等关键词 → SYSTEM\n" +
               "3. 包含告警、alert、预警、通知等关键词 → ALERT\n" +
               "4. 你好、谢谢、再见等问候语 → CHAT\n" +
               "5. 问题已解决或用户表示感谢 → FINISH\n\n" +
               "【重要】只输出一个词，不要有任何其他内容。";
    }
}
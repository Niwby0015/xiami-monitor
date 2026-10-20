package com.ruoyi.agent.node;

import com.ruoyi.agent.dto.AgentResponse;
import com.ruoyi.monitor.n9e.service.IN9eService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AlertAgentNode {

    private static final Logger log = LoggerFactory.getLogger(AlertAgentNode.class);

    private final ChatLanguageModel model;

    @Autowired
    private IN9eService n9eService;

    public AlertAgentNode() {
        this.model = OpenAiChatModel.builder()
                .apiKey("")
                .baseUrl("https://api.deepseek.com/v1")
                .modelName("deepseek-chat")
                .temperature(0.0)
                .build();
    }

    public AgentResponse process(String userMessage, String context) {
        log.info("[告警助手] 处理请求: {}", userMessage);
        
        if (userMessage.contains("查询") || userMessage.contains("查看") || 
            userMessage.contains("历史") || userMessage.contains("活跃") || userMessage.contains("当前")) {
            return createAlertTable(userMessage);
        }
        
        if (userMessage.contains("配置") || userMessage.contains("创建") || 
            userMessage.contains("添加") || userMessage.contains("规则")) {
            return createAlertConfigForm();
        }
        
        String systemPrompt = "你是告警管理专家。帮助用户：\n" +
                             "1. 查询和分析告警信息\n" +
                             "2. 配置告警规则\n" +
                             "3. 提供告警处理建议\n\n" +
                             "请用专业的语言回答，必要时使用表格展示数据。";
        
        String reply = model.generate(systemPrompt + "\n\n用户: " + userMessage);
        
        AgentResponse response = new AgentResponse();
        response.setReply(reply);
        response.setType("text");
        return response;
    }

    private AgentResponse createAlertTable(String userMessage) {
        AgentResponse response = new AgentResponse();
        
        try {
            if (userMessage.contains("活跃") || userMessage.contains("当前") || userMessage.contains("进行中")) {
                return queryActiveAlerts();
            } else if (userMessage.contains("历史")) {
                return queryHistoryAlerts(userMessage);
            } else if (userMessage.contains("规则")) {
                return queryAlertRules();
            } else {
                return queryActiveAlerts();
            }
        } catch (Exception e) {
            log.error("[告警助手] 查询告警失败", e);
            return createErrorResponse("查询告警信息失败：" + e.getMessage());
        }
    }

    private AgentResponse queryActiveAlerts() {
        try {
            log.info("[告警助手] 查询活跃告警");
            Map<String, Object> params = new HashMap<>();
            params.put("p", 1);
            params.put("limit", 20);
            
            Object result = n9eService.listActiveAlerts(params);
            
            if (result == null) {
                AgentResponse response = new AgentResponse();
                response.setReply("当前没有活跃告警。");
                response.setType("text");
                return response;
            }
            
            List<Map<String, Object>> alerts = convertToList(result);
            if (alerts == null || alerts.isEmpty()) {
                AgentResponse response = new AgentResponse();
                response.setReply("当前没有活跃告警。");
                response.setType("text");
                return response;
            }
            
            AgentResponse agentResponse = new AgentResponse();
            agentResponse.setReply("以下是当前活跃告警（共 " + alerts.size() + " 条）：");
            agentResponse.setType("table");
            
            List<AgentResponse.TableColumn> columns = new ArrayList<>();
            columns.add(createColumn("severity", "级别", 80));
            columns.add(createColumn("rule_name", "规则名称", 200));
            columns.add(createColumn("target", "目标", 150));
            columns.add(createColumn("trigger_value", "触发值", 100));
            columns.add(createColumn("first_trigger_time", "首次触发时间", 180));
            columns.add(createColumn("notify_groups", "通知组", 120));
            
            agentResponse.setTableColumns(columns);
            
            List<Map<String, Object>> tableData = new ArrayList<>();
            for (Map<String, Object> alert : alerts) {
                Map<String, Object> row = new HashMap<>();
                row.put("severity", getSeverityText(alert.get("severity")));
                row.put("rule_name", alert.get("rule_name"));
                row.put("target", alert.get("target"));
                row.put("trigger_value", alert.get("trigger_value"));
                row.put("first_trigger_time", formatTimestamp(alert.get("first_trigger_time")));
                row.put("notify_groups", alert.get("notify_groups"));
                tableData.add(row);
            }
            
            agentResponse.setTableData(tableData);
            return agentResponse;
            
        } catch (Exception e) {
            log.error("[告警助手] 查询活跃告警失败", e);
            return createErrorResponse("查询活跃告警失败：" + e.getMessage());
        }
    }

    private AgentResponse queryHistoryAlerts(String userMessage) {
        try {
            log.info("[告警助手] 查询历史告警");
            Map<String, Object> params = new HashMap<>();
            params.put("p", 1);
            params.put("limit", 20);
            
            Object result = n9eService.listHistoryAlerts(params);
            
            if (result == null) {
                AgentResponse response = new AgentResponse();
                response.setReply("暂无历史告警记录。");
                response.setType("text");
                return response;
            }
            
            List<Map<String, Object>> alerts = convertToList(result);
            if (alerts == null || alerts.isEmpty()) {
                AgentResponse response = new AgentResponse();
                response.setReply("暂无历史告警记录。");
                response.setType("text");
                return response;
            }
            
            AgentResponse agentResponse = new AgentResponse();
            agentResponse.setReply("以下是历史告警记录（共 " + alerts.size() + " 条）：");
            agentResponse.setType("table");
            
            List<AgentResponse.TableColumn> columns = new ArrayList<>();
            columns.add(createColumn("severity", "级别", 80));
            columns.add(createColumn("rule_name", "规则名称", 200));
            columns.add(createColumn("target", "目标", 150));
            columns.add(createColumn("trigger_value", "触发值", 100));
            columns.add(createColumn("first_trigger_time", "首次触发时间", 180));
            columns.add(createColumn("last_eval_time", "最后评估时间", 180));
            columns.add(createColumn("status", "状态", 80));
            
            agentResponse.setTableColumns(columns);
            
            List<Map<String, Object>> tableData = new ArrayList<>();
            for (Map<String, Object> alert : alerts) {
                Map<String, Object> row = new HashMap<>();
                row.put("severity", getSeverityText(alert.get("severity")));
                row.put("rule_name", alert.get("rule_name"));
                row.put("target", alert.get("target"));
                row.put("trigger_value", alert.get("trigger_value"));
                row.put("first_trigger_time", formatTimestamp(alert.get("first_trigger_time")));
                row.put("last_eval_time", formatTimestamp(alert.get("last_eval_time")));
                row.put("status", "已恢复");
                tableData.add(row);
            }
            
            agentResponse.setTableData(tableData);
            return agentResponse;
            
        } catch (Exception e) {
            log.error("[告警助手] 查询历史告警失败", e);
            return createErrorResponse("查询历史告警失败：" + e.getMessage());
        }
    }

    private AgentResponse queryAlertRules() {
        try {
            log.info("[告警助手] 查询告警规则");
            Map<String, Object> params = new HashMap<>();
            params.put("p", 1);
            params.put("limit", 20);
            
            Object result = n9eService.listAlertRules(params);
            
            if (result == null) {
                AgentResponse response = new AgentResponse();
                response.setReply("暂无告警规则。");
                response.setType("text");
                return response;
            }
            
            Map<String, Object> resultMap = convertToMap(result);
            Object datObj = resultMap.get("dat");
            List<Map<String, Object>> rules = convertToList(datObj);
            
            if (rules == null || rules.isEmpty()) {
                AgentResponse response = new AgentResponse();
                response.setReply("暂无告警规则。");
                response.setType("text");
                return response;
            }
            
            AgentResponse agentResponse = new AgentResponse();
            agentResponse.setReply("以下是告警规则列表（共 " + rules.size() + " 条）：");
            agentResponse.setType("table");
            
            List<AgentResponse.TableColumn> columns = new ArrayList<>();
            columns.add(createColumn("id", "规则ID", 80));
            columns.add(createColumn("name", "规则名称", 200));
            columns.add(createColumn("severity", "级别", 80));
            columns.add(createColumn("enabled", "状态", 80));
            columns.add(createColumn("append_tags", "标签", 150));
            
            agentResponse.setTableColumns(columns);
            
            List<Map<String, Object>> tableData = new ArrayList<>();
            for (Map<String, Object> rule : rules) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rule.get("id"));
                row.put("name", rule.get("name"));
                row.put("severity", getSeverityText(rule.get("severity")));
                row.put("enabled", Boolean.TRUE.equals(rule.get("enabled")) ? "启用" : "禁用");
                row.put("append_tags", rule.get("append_tags"));
                tableData.add(row);
            }
            
            agentResponse.setTableData(tableData);
            return agentResponse;
            
        } catch (Exception e) {
            log.error("[告警助手] 查询告警规则失败", e);
            return createErrorResponse("查询告警规则失败：" + e.getMessage());
        }
    }

    private String getSeverityText(Object severity) {
        if (severity == null) return "未知";
        String sev = String.valueOf(severity);
        switch (sev) {
            case "1": return "严重";
            case "2": return "警告";
            case "3": return "提示";
            default: return sev;
        }
    }

    private String formatTimestamp(Object timestamp) {
        if (timestamp == null) return "-";
        try {
            long ts = Long.parseLong(String.valueOf(timestamp));
            java.time.Instant instant = java.time.Instant.ofEpochSecond(ts);
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneId.systemDefault());
            return formatter.format(instant);
        } catch (Exception e) {
            return String.valueOf(timestamp);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertToList(Object obj) {
        if (obj instanceof List) {
            return (List<Map<String, Object>>) obj;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return new HashMap<>();
    }

    private AgentResponse createAlertConfigForm() {
        AgentResponse response = new AgentResponse();
        response.setReply("请填写告警规则配置：");
        response.setType("form");
        response.setFormTitle("🔔 告警规则配置");
        response.setFormDescription("配置新的告警规则，当满足条件时会触发告警通知。");
        
        List<AgentResponse.FormField> fields = new ArrayList<>();
        
        AgentResponse.FormField nameField = new AgentResponse.FormField();
        nameField.setKey("name");
        nameField.setLabel("告警名称");
        nameField.setType("input");
        nameField.setPlaceholder("例如：CPU使用率告警");
        fields.add(nameField);
        
        AgentResponse.FormField levelField = new AgentResponse.FormField();
        levelField.setKey("level");
        levelField.setLabel("告警级别");
        levelField.setType("select");
        levelField.setPlaceholder("请选择告警级别");
        
        List<AgentResponse.Option> options = new ArrayList<>();
        options.add(createOption("提示", "3"));
        options.add(createOption("警告", "2"));
        options.add(createOption("严重", "1"));
        levelField.setOptions(options);
        fields.add(levelField);
        
        AgentResponse.FormField thresholdField = new AgentResponse.FormField();
        thresholdField.setKey("threshold");
        thresholdField.setLabel("触发阈值");
        thresholdField.setType("number");
        thresholdField.setMin(0);
        thresholdField.setMax(100);
        fields.add(thresholdField);
        
        response.setFormFields(fields);
        response.setConfirmText("保存规则");
        response.setCancelText("取消");
        response.setFormId("alert_config_" + System.currentTimeMillis());
        
        return response;
    }

    private AgentResponse.Option createOption(String label, String value) {
        AgentResponse.Option option = new AgentResponse.Option();
        option.setLabel(label);
        option.setValue(value);
        return option;
    }

    private AgentResponse.TableColumn createColumn(String prop, String label, Integer width) {
        AgentResponse.TableColumn column = new AgentResponse.TableColumn();
        column.setProp(prop);
        column.setLabel(label);
        column.setWidth(width);
        return column;
    }

    private AgentResponse createErrorResponse(String errorMsg) {
        AgentResponse response = new AgentResponse();
        response.setReply("❌ " + errorMsg);
        response.setType("text");
        return response;
    }
}
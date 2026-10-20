package com.ruoyi.agent.node;

import com.ruoyi.agent.dto.AgentResponse;
import com.ruoyi.system.domain.vo.K8sPodVO;
import com.ruoyi.system.service.IKubernetesService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
 
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class K8sAgentNode {

    private static final Logger log = LoggerFactory.getLogger(K8sAgentNode.class);

    private final ChatLanguageModel model;

    @Autowired(required = false)
    private IKubernetesService kubernetesService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public K8sAgentNode() {
        this.model = OpenAiChatModel.builder()
                .apiKey("")
                .baseUrl("https://api.deepseek.com/v1")
                .modelName("deepseek-chat")
                .temperature(0.0)
                .build();
    }

    public AgentResponse process(String userMessage, String context) {
        log.info("[K8s助手] 处理请求: {}", userMessage);
        
        if (needsApproval(userMessage)) {
            return createApprovalForm(userMessage);
        }
        
        if (isQueryRequest(userMessage)) {
            return createTableResponse(userMessage);
        }
        
        String systemPrompt = buildSystemPrompt();
        String fullPrompt = systemPrompt + "\n\n=== 对话历史 ===\n" + context + 
                           "\n\n=== 用户问题 ===\n" + userMessage;
        
        String reply = model.generate(fullPrompt);
        
        AgentResponse response = new AgentResponse();
        response.setReply(reply);
        response.setType("text");
        return response;
    }

    private String buildSystemPrompt() {
        return "你是Kubernetes运维专家助手。你的职责：\n" +
               "1. 帮助用户查询K8s集群资源状态\n" +
               "2. 协助排查K8s相关问题\n" +
               "3. 执行高危操作前必须提供审批表单\n\n" +
               "回答要求：\n" +
               "- 使用清晰的Markdown格式\n" +
               "- 对于资源列表，说明会使用表格展示\n" +
               "- 高危操作（删除、重启、扩缩容）必须先获得审批\n" +
               "- 提供专业的技术建议";
    }

    private boolean needsApproval(String message) {
        return message.contains("删除") || message.contains("重启") || 
               message.contains("扩缩容") || message.contains("更新配置");
    }

    private boolean isQueryRequest(String message) {
        return message.contains("查看") || message.contains("查询") || 
               message.contains("列表") || message.contains("状态");
    }

    private AgentResponse createApprovalForm(String userMessage) {
        AgentResponse response = new AgentResponse();
        response.setReply("在执行此操作前，需要您的确认：");
        response.setType("form");
        response.setFormTitle("⚠️ 运维操作审批");
        response.setFormDescription("您即将执行一个可能影响系统稳定性的操作，请仔细确认后继续。");
        
        List<AgentResponse.FormField> fields = new ArrayList<>();
        
        AgentResponse.FormField confirmField = new AgentResponse.FormField();
        confirmField.setKey("confirm");
        confirmField.setLabel("我已知晓风险");
        confirmField.setType("switch");
        fields.add(confirmField);
        
        AgentResponse.FormField reasonField = new AgentResponse.FormField();
        reasonField.setKey("reason");
        reasonField.setLabel("操作原因");
        reasonField.setType("input");
        reasonField.setPlaceholder("请说明为什么要执行此操作");
        fields.add(reasonField);
        
        AgentResponse.FormField scopeField = new AgentResponse.FormField();
        scopeField.setKey("scope");
        scopeField.setLabel("影响范围");
        scopeField.setType("select");
        scopeField.setPlaceholder("请选择影响范围");
        
        List<AgentResponse.Option> options = new ArrayList<>();
        options.add(createOption("单个Pod", "single_pod"));
        options.add(createOption("整个Deployment", "deployment"));
        options.add(createOption("整个命名空间", "namespace"));
        scopeField.setOptions(options);
        fields.add(scopeField);
        
        response.setFormFields(fields);
        response.setConfirmText("确认执行");
        response.setCancelText("取消操作");
        response.setFormId("k8s_operation_" + System.currentTimeMillis());
        
        return response;
    }

    private AgentResponse createTableResponse(String userMessage) {
        AgentResponse response = new AgentResponse();
        
        String namespace = extractNamespace(userMessage);
        log.info("[K8s助手] 提取到的命名空间: {}", namespace);
        
        if (kubernetesService == null) {
            log.warn("[K8s助手] K8s服务未注入，返回提示信息");
            response.setReply("⚠️ K8s集群服务未配置，暂时无法查询集群资源。请联系管理员配置 K8s 连接。");
            response.setType("text");
            return response;
        }
        
        try {
            log.info("[K8s助手] 开始查询K8s集群，超时时间5秒");
            
            Future<List<K8sPodVO>> future = executorService.submit(() -> {
                return kubernetesService.getPods(namespace, null);
            });
            
            List<K8sPodVO> pods = future.get(5, TimeUnit.SECONDS);
            
            if (pods == null || pods.isEmpty()) {
                response.setReply("在命名空间 \"" + namespace + "\" 下没有找到 Pod 资源。");
                response.setType("text");
                return response;
            }
            
            List<AgentResponse.TableColumn> columns = new ArrayList<>();
            columns.add(createColumn("name", "名称", 250));
            columns.add(createColumn("namespace", "命名空间", 120));
            columns.add(createColumn("status", "状态", 100));
            columns.add(createColumn("restarts", "重启次数", 100));
            columns.add(createColumn("age", "运行时长", 120));
            columns.add(createColumn("podIp", "Pod IP", 140));
            columns.add(createColumn("nodeName", "所在节点", 150));
            
            response.setTableColumns(columns);
            
            List<Map<String, Object>> data = new ArrayList<>();
            for (K8sPodVO pod : pods) {
                Map<String, Object> row = new HashMap<>();
                row.put("name", pod.getName());
                row.put("namespace", pod.getNamespace());
                row.put("status", pod.getStatus());
                row.put("restarts", pod.getRestartCount());
                row.put("age", calculateAge(pod.getCreationTimestamp()));
                row.put("podIp", pod.getPodIp());
                row.put("nodeName", pod.getNodeName());
                data.add(row);
            }
            
            response.setTableData(data);
            response.setReply("以下是命名空间 \"" + namespace + "\" 下的 Pod 信息（共 " + pods.size() + " 个）：");
            response.setType("table");
            
        } catch (TimeoutException e) {
            log.error("[K8s助手] 查询K8s集群超时", e);
            response.setReply("⏱️ 查询K8s集群超时，请检查：\n" +
                            "1. K8s集群是否正常运行\n" +
                            "2. 网络连接是否正常\n" +
                            "3. Kubeconfig配置是否正确\n\n" +
                            "如果服务部署在K8s集群内，请检查ServiceAccount权限。");
            response.setType("text");
        } catch (Exception e) {
            log.error("[K8s助手] 查询K8s集群失败", e);
            response.setReply("❌ 查询K8s集群失败：" + e.getMessage() + 
                            "\n\n请检查K8s集群连接配置是否正确。");
            response.setType("text");
        }
        
        return response;
    }

    private String extractNamespace(String userMessage) {
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_-]+)命名空间");
        Matcher matcher = pattern.matcher(userMessage);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        pattern = Pattern.compile("namespace[：:\\s]+([a-zA-Z0-9_-]+)");
        matcher = pattern.matcher(userMessage);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return "default";
    }

    private String calculateAge(String creationTimestamp) {
        if (creationTimestamp == null) {
            return "未知";
        }
        
        try {
            java.time.Instant created = java.time.Instant.parse(creationTimestamp);
            java.time.Duration duration = java.time.Duration.between(created, java.time.Instant.now());
            
            long days = duration.toDays();
            long hours = duration.toHours() % 24;
            
            if (days > 0) {
                return days + "d";
            } else if (hours > 0) {
                return hours + "h";
            } else {
                return duration.toMinutes() + "m";
            }
        } catch (Exception e) {
            return "未知";
        }
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
}
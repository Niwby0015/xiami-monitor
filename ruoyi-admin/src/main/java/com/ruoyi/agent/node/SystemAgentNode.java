package com.ruoyi.agent.node;

import com.ruoyi.agent.dto.AgentResponse;
import com.ruoyi.device.domain.DatabaseMaster;
import com.ruoyi.device.domain.JarsAppMaster;
import com.ruoyi.device.domain.MiddlewareMaster;
import com.ruoyi.device.domain.PhysicalMaster;
import com.ruoyi.device.domain.VirtualMaster;
import com.ruoyi.device.service.IDatabaseMasterService;
import com.ruoyi.device.service.IJarsAppMasterService;
import com.ruoyi.device.service.IMiddlewareMasterService;
import com.ruoyi.device.service.IPhysicalMasterService;
import com.ruoyi.device.service.IVirtualMasterService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SystemAgentNode {

    private static final Logger log = LoggerFactory.getLogger(SystemAgentNode.class);

    private final ChatLanguageModel model;

    @Autowired
    private IPhysicalMasterService physicalMasterService;

    @Autowired
    private IDatabaseMasterService databaseMasterService;

    @Autowired
    private IMiddlewareMasterService middlewareMasterService;

    @Autowired
    private IVirtualMasterService virtualMasterService;

    @Autowired
    private IJarsAppMasterService jarsAppMasterService;

    public SystemAgentNode() {
        this.model = OpenAiChatModel.builder()
                .apiKey("")
                .baseUrl("https://api.deepseek.com/v1")
                .modelName("deepseek-chat")
                .temperature(0.0)
                .build();
    }

    public AgentResponse process(String userMessage, String context) {
        log.info("[系统助手] 处理请求: {}", userMessage);
        
        OperationType operationType = detectOperationType(userMessage);
        
        switch (operationType) {
            case QUERY_PHYSICAL:
                return handlePhysicalMachineQuery(userMessage);
                
            case QUERY_DATABASE:
                return handleDatabaseQuery(userMessage);
                
            case QUERY_MIDDLEWARE:
                return handleMiddlewareQuery(userMessage);
                
            case QUERY_VIRTUAL:
                return handleVirtualMachineQuery(userMessage);
                
            case QUERY_JARS:
                return handleJarsAppQuery(userMessage);
                
            case ADD_DEVICE:
                return createAddDeviceForm(userMessage);
                
            case UPDATE_DEVICE:
                return createUpdateDeviceForm(userMessage);
                
            case DELETE_DEVICE:
                return createDeleteConfirmationForm(userMessage);
                
            default:
                return handleGeneralQuery(userMessage, context);
        }
    }

    private OperationType detectOperationType(String message) {
        String lowerMsg = message.toLowerCase();
        
        if (lowerMsg.contains("删除") || lowerMsg.contains("移除")) {
            return OperationType.DELETE_DEVICE;
        }
        
        if (lowerMsg.contains("添加") || lowerMsg.contains("新增") || 
            lowerMsg.contains("创建") || lowerMsg.contains("注册")) {
            return OperationType.ADD_DEVICE;
        }
        
        if (lowerMsg.contains("修改") || lowerMsg.contains("更新") || 
            lowerMsg.contains("编辑")) {
            return OperationType.UPDATE_DEVICE;
        }
        
        if (lowerMsg.contains("物理机") || lowerMsg.contains("服务器") || 
            lowerMsg.contains("主机")) {
            return OperationType.QUERY_PHYSICAL;
        }
        
        if (lowerMsg.contains("数据库") || lowerMsg.contains("mysql") || 
            lowerMsg.contains("redis") || lowerMsg.contains("oracle")) {
            return OperationType.QUERY_DATABASE;
        }
        
        if (lowerMsg.contains("中间件") || lowerMsg.contains("nginx") || 
            lowerMsg.contains("tomcat") || lowerMsg.contains("kafka")) {
            return OperationType.QUERY_MIDDLEWARE;
        }
        
        if (lowerMsg.contains("虚拟机") || lowerMsg.contains("vm")) {
            return OperationType.QUERY_VIRTUAL;
        }
        
        if (lowerMsg.contains("jar") || lowerMsg.contains("应用")) {
            return OperationType.QUERY_JARS;
        }
        
        return OperationType.GENERAL_QUERY;
    }

    private AgentResponse handlePhysicalMachineQuery(String userMessage) {
        try {
            log.info("[系统助手] 查询物理机列表");
            PhysicalMaster query = new PhysicalMaster();
            List<PhysicalMaster> list = physicalMasterService.selectPhysicalMasterList(query);
            
            if (list == null || list.isEmpty()) {
                AgentResponse response = new AgentResponse();
                response.setReply("暂无物理机信息。");
                response.setType("text");
                return response;
            }
            
            AgentResponse agentResponse = new AgentResponse();
            agentResponse.setReply("以下是物理机列表信息（共 " + list.size() + " 台）：");
            agentResponse.setType("table");
            
            List<AgentResponse.TableColumn> columns = new ArrayList<>();
            columns.add(createColumn("physicalId", "物理机ID", 100));
            columns.add(createColumn("physicalName", "物理机名称", 150));
            columns.add(createColumn("brand", "品牌", 100));
            columns.add(createColumn("model", "型号", 120));
            columns.add(createColumn("ipAddress", "IP地址", 130));
            columns.add(createColumn("macAddress", "MAC地址", 140));
            columns.add(createColumn("leader", "负责人", 100));
            columns.add(createColumn("status", "状态", 80));
            
            agentResponse.setTableColumns(columns);
            
            List<Map<String, Object>> tableData = new ArrayList<>();
            for (PhysicalMaster pm : list) {
                Map<String, Object> row = new HashMap<>();
                row.put("physicalId", pm.getPhysicalId());
                row.put("physicalName", pm.getPhysicalName());
                row.put("brand", pm.getBrand());
                row.put("model", pm.getModel());
                row.put("ipAddress", pm.getIpAddress());
                row.put("macAddress", pm.getMacAddress());
                row.put("leader", pm.getLeader());
                row.put("status", "0".equals(String.valueOf(pm.getStatus())) ? "正常" : "禁用");
                tableData.add(row);
            }
            
            agentResponse.setTableData(tableData);
            return agentResponse;
        } catch (Exception e) {
            log.error("[系统助手] 查询物理机失败", e);
            return createErrorResponse("查询物理机信息失败：" + e.getMessage());
        }
    }

    private AgentResponse handleDatabaseQuery(String userMessage) {
        try {
            log.info("[系统助手] 查询数据库列表");
            DatabaseMaster query = new DatabaseMaster();
            List<DatabaseMaster> list = databaseMasterService.selectDatabaseMasterList(query);
            
            if (list == null || list.isEmpty()) {
                AgentResponse response = new AgentResponse();
                response.setReply("暂无数据库实例信息。");
                response.setType("text");
                return response;
            }
            
            AgentResponse agentResponse = new AgentResponse();
            agentResponse.setReply("以下是数据库实例列表（共 " + list.size() + " 个）：");
            agentResponse.setType("table");
            
            List<AgentResponse.TableColumn> columns = new ArrayList<>();
            columns.add(createColumn("databaseId", "数据库ID", 100));
            columns.add(createColumn("databaseName", "数据库名称", 150));
            columns.add(createColumn("ipAddress", "IP地址", 130));
            columns.add(createColumn("port", "端口", 80));
            columns.add(createColumn("version", "版本", 100));
            columns.add(createColumn("leader", "负责人", 100));
            columns.add(createColumn("status", "状态", 80));
            
            agentResponse.setTableColumns(columns);
            
            List<Map<String, Object>> tableData = new ArrayList<>();
            for (DatabaseMaster db : list) {
                Map<String, Object> row = new HashMap<>();
                row.put("databaseId", db.getDatabaseId());
                row.put("databaseName", db.getDatabaseName());
                row.put("ipAddress", db.getIpAddress());
                row.put("port", db.getPort());
                row.put("version", db.getVersion());
                row.put("leader", db.getLeader());
                row.put("status", "0".equals(String.valueOf(db.getStatus())) ? "正常" : "禁用");
                tableData.add(row);
            }
            
            agentResponse.setTableData(tableData);
            return agentResponse;
        } catch (Exception e) {
            log.error("[系统助手] 查询数据库失败", e);
            return createErrorResponse("查询数据库信息失败：" + e.getMessage());
        }
    }

    private AgentResponse handleMiddlewareQuery(String userMessage) {
        try {
            log.info("[系统助手] 查询中间件列表");
            MiddlewareMaster query = new MiddlewareMaster();
            List<MiddlewareMaster> list = middlewareMasterService.selectMiddlewareMasterList(query);
            
            if (list == null || list.isEmpty()) {
                AgentResponse response = new AgentResponse();
                response.setReply("暂无中间件信息。");
                response.setType("text");
                return response;
            }
            
            AgentResponse agentResponse = new AgentResponse();
            agentResponse.setReply("以下是中间件列表（共 " + list.size() + " 个）：");
            agentResponse.setType("table");
            
            List<AgentResponse.TableColumn> columns = new ArrayList<>();
            columns.add(createColumn("middlewareId", "中间件ID", 100));
            columns.add(createColumn("middlewareName", "中间件名称", 150));
            columns.add(createColumn("middlewareVersion", "版本", 100));
            columns.add(createColumn("deployType", "部署方式", 100));
            columns.add(createColumn("ipAddress", "IP地址", 130));
            columns.add(createColumn("port", "端口", 80));
            columns.add(createColumn("envType", "环境", 80));
            columns.add(createColumn("status", "状态", 100));
            
            agentResponse.setTableColumns(columns);
            
            List<Map<String, Object>> tableData = new ArrayList<>();
            for (MiddlewareMaster mw : list) {
                Map<String, Object> row = new HashMap<>();
                row.put("middlewareId", mw.getMiddlewareId());
                row.put("middlewareName", mw.getMiddlewareName());
                row.put("middlewareVersion", mw.getMiddlewareVersion());
                row.put("deployType", mw.getDeployType());
                row.put("ipAddress", mw.getIpAddress());
                row.put("port", mw.getPort());
                row.put("envType", mw.getEnvType());
                
                String status = String.valueOf(mw.getStatus());
                String statusText = "0".equals(status) ? "正常" : 
                                   "1".equals(status) ? "维护中" : "未启用";
                row.put("status", statusText);
                
                tableData.add(row);
            }
            
            agentResponse.setTableData(tableData);
            return agentResponse;
        } catch (Exception e) {
            log.error("[系统助手] 查询中间件失败", e);
            return createErrorResponse("查询中间件信息失败：" + e.getMessage());
        }
    }

    private AgentResponse handleVirtualMachineQuery(String userMessage) {
        try {
            log.info("[系统助手] 查询虚拟机列表");
            VirtualMaster query = new VirtualMaster();
            List<VirtualMaster> list = virtualMasterService.selectVirtualMasterList(query);
            
            if (list == null || list.isEmpty()) {
                AgentResponse response = new AgentResponse();
                response.setReply("暂无虚拟机信息。");
                response.setType("text");
                return response;
            }
            
            AgentResponse agentResponse = new AgentResponse();
            agentResponse.setReply("以下是虚拟机列表（共 " + list.size() + " 台）：");
            agentResponse.setType("table");
            
            List<AgentResponse.TableColumn> columns = new ArrayList<>();
            columns.add(createColumn("virtualId", "虚拟机ID", 100));
            columns.add(createColumn("virtualName", "虚拟机名称", 150));
            columns.add(createColumn("ipAddress", "IP地址", 130));
            columns.add(createColumn("osType", "操作系统", 120));
            columns.add(createColumn("leader", "负责人", 100));
            columns.add(createColumn("status", "状态", 80));
            
            agentResponse.setTableColumns(columns);
            
            List<Map<String, Object>> tableData = new ArrayList<>();
            for (VirtualMaster vm : list) {
                Map<String, Object> row = new HashMap<>();
                row.put("virtualId", vm.getVirtualId());
                row.put("virtualName", vm.getVirtualName());
                row.put("ipAddress", vm.getIpAddress());
                row.put("osType", vm.getOsType());
                row.put("leader", vm.getLeader());
                row.put("status", "0".equals(String.valueOf(vm.getStatus())) ? "运行中" : "已停止");
                tableData.add(row);
            }
            
            agentResponse.setTableData(tableData);
            return agentResponse;
        } catch (Exception e) {
            log.error("[系统助手] 查询虚拟机失败", e);
            return createErrorResponse("查询虚拟机信息失败：" + e.getMessage());
        }
    }

    private AgentResponse handleJarsAppQuery(String userMessage) {
        try {
            log.info("[系统助手] 查询JAR应用列表");
            JarsAppMaster query = new JarsAppMaster();
            List<JarsAppMaster> list = jarsAppMasterService.selectJarsAppMasterList(query);
            
            if (list == null || list.isEmpty()) {
                AgentResponse response = new AgentResponse();
                response.setReply("暂无JAR应用信息。");
                response.setType("text");
                return response;
            }
            
            AgentResponse agentResponse = new AgentResponse();
            agentResponse.setReply("以下是JAR应用列表（共 " + list.size() + " 个）：");
            agentResponse.setType("table");
            
            List<AgentResponse.TableColumn> columns = new ArrayList<>();
            columns.add(createColumn("jarsId", "应用ID", 100));
            columns.add(createColumn("appName", "应用名称", 150));
            columns.add(createColumn("appNameCn", "中文名称", 150));
            columns.add(createColumn("javaVersion", "Java版本", 100));
            columns.add(createColumn("deployPath", "部署路径", 200));
            columns.add(createColumn("port", "端口", 80));
            columns.add(createColumn("leader", "负责人", 100));
            columns.add(createColumn("status", "状态", 80));
            
            agentResponse.setTableColumns(columns);
            
            List<Map<String, Object>> tableData = new ArrayList<>();
            for (JarsAppMaster app : list) {
                Map<String, Object> row = new HashMap<>();
                row.put("jarsId", app.getJarsId());
                row.put("appName", app.getAppName());
                row.put("appNameCn", app.getAppNameCn());
                row.put("javaVersion", app.getJavaVersion());
                row.put("deployPath", app.getDeployPath());
                row.put("port", app.getPort());
                row.put("leader", app.getLeader());
                row.put("status", "0".equals(String.valueOf(app.getStatus())) ? "运行中" : "已停止");
                tableData.add(row);
            }
            
            agentResponse.setTableData(tableData);
            return agentResponse;
        } catch (Exception e) {
            log.error("[系统助手] 查询JAR应用失败", e);
            return createErrorResponse("查询JAR应用信息失败：" + e.getMessage());
        }
    }

    private AgentResponse createAddDeviceForm(String userMessage) {
        AgentResponse response = new AgentResponse();
        response.setReply("请填写新设备的信息：");
        response.setType("form");
        response.setFormTitle("➕ 添加新设备");
        response.setFormDescription("请完整填写设备信息，带*号为必填项。");
        
        List<AgentResponse.FormField> fields = new ArrayList<>();
        
        AgentResponse.FormField typeField = new AgentResponse.FormField();
        typeField.setKey("deviceType");
        typeField.setLabel("设备类型*");
        typeField.setType("select");
        typeField.setPlaceholder("请选择设备类型");
        
        List<AgentResponse.Option> options = new ArrayList<>();
        options.add(createOption("物理机", "physical"));
        options.add(createOption("虚拟机", "virtual"));
        options.add(createOption("数据库", "database"));
        options.add(createOption("中间件", "middleware"));
        options.add(createOption("JAR应用", "jars"));
        typeField.setOptions(options);
        fields.add(typeField);
        
        AgentResponse.FormField nameField = new AgentResponse.FormField();
        nameField.setKey("deviceName");
        nameField.setLabel("设备名称*");
        nameField.setType("input");
        nameField.setPlaceholder("请输入设备名称");
        fields.add(nameField);
        
        AgentResponse.FormField ipField = new AgentResponse.FormField();
        ipField.setKey("ipAddress");
        ipField.setLabel("IP地址*");
        ipField.setType("input");
        ipField.setPlaceholder("例如：192.168.1.100");
        fields.add(ipField);
        
        AgentResponse.FormField portField = new AgentResponse.FormField();
        portField.setKey("port");
        portField.setLabel("端口");
        portField.setType("input");
        portField.setPlaceholder("例如：3306, 8080");
        fields.add(portField);
        
        AgentResponse.FormField leaderField = new AgentResponse.FormField();
        leaderField.setKey("leader");
        leaderField.setLabel("负责人*");
        leaderField.setType("input");
        leaderField.setPlaceholder("请输入负责人姓名");
        fields.add(leaderField);
        
        response.setFormFields(fields);
        response.setConfirmText("确认添加");
        response.setCancelText("取消");
        response.setFormId("add_device_" + System.currentTimeMillis());
        
        return response;
    }

    private AgentResponse createUpdateDeviceForm(String userMessage) {
        AgentResponse response = new AgentResponse();
        response.setReply("请选择要修改的设备并填写新信息：");
        response.setType("form");
        response.setFormTitle("✏️ 修改设备信息");
        response.setFormDescription("请先选择设备类型和设备，然后填写需要修改的字段。");
        
        List<AgentResponse.FormField> fields = new ArrayList<>();
        
        AgentResponse.FormField typeField = new AgentResponse.FormField();
        typeField.setKey("deviceType");
        typeField.setLabel("设备类型*");
        typeField.setType("select");
        typeField.setPlaceholder("请选择设备类型");
        
        List<AgentResponse.Option> options = new ArrayList<>();
        options.add(createOption("物理机", "physical"));
        options.add(createOption("虚拟机", "virtual"));
        options.add(createOption("数据库", "database"));
        options.add(createOption("中间件", "middleware"));
        options.add(createOption("JAR应用", "jars"));
        typeField.setOptions(options);
        fields.add(typeField);
        
        AgentResponse.FormField idField = new AgentResponse.FormField();
        idField.setKey("deviceId");
        idField.setLabel("设备ID*");
        idField.setType("input");
        idField.setPlaceholder("请输入要修改的设备ID");
        fields.add(idField);
        
        AgentResponse.FormField updateField = new AgentResponse.FormField();
        updateField.setKey("updateField");
        updateField.setLabel("修改字段*");
        updateField.setType("select");
        updateField.setPlaceholder("请选择要修改的字段");
        
        List<AgentResponse.Option> fieldOptions = new ArrayList<>();
        fieldOptions.add(createOption("设备名称", "name"));
        fieldOptions.add(createOption("IP地址", "ip"));
        fieldOptions.add(createOption("端口", "port"));
        fieldOptions.add(createOption("负责人", "leader"));
        fieldOptions.add(createOption("状态", "status"));
        updateField.setOptions(fieldOptions);
        fields.add(updateField);
        
        AgentResponse.FormField valueField = new AgentResponse.FormField();
        valueField.setKey("newValue");
        valueField.setLabel("新值*");
        valueField.setType("input");
        valueField.setPlaceholder("请输入新的值");
        fields.add(valueField);
        
        response.setFormFields(fields);
        response.setConfirmText("确认修改");
        response.setCancelText("取消");
        response.setFormId("update_device_" + System.currentTimeMillis());
        
        return response;
    }

    private AgentResponse createDeleteConfirmationForm(String userMessage) {
        AgentResponse response = new AgentResponse();
        response.setReply("⚠️ 删除操作不可恢复，请谨慎确认！");
        response.setType("form");
        response.setFormTitle("🗑️ 删除设备确认");
        response.setFormDescription("警告：删除操作将永久移除设备信息，请确认后再执行。");
        
        List<AgentResponse.FormField> fields = new ArrayList<>();
        
        AgentResponse.FormField typeField = new AgentResponse.FormField();
        typeField.setKey("deviceType");
        typeField.setLabel("设备类型*");
        typeField.setType("select");
        typeField.setPlaceholder("请选择设备类型");
        
        List<AgentResponse.Option> options = new ArrayList<>();
        options.add(createOption("物理机", "physical"));
        options.add(createOption("虚拟机", "virtual"));
        options.add(createOption("数据库", "database"));
        options.add(createOption("中间件", "middleware"));
        options.add(createOption("JAR应用", "jars"));
        typeField.setOptions(options);
        fields.add(typeField);
        
        AgentResponse.FormField idField = new AgentResponse.FormField();
        idField.setKey("deviceIds");
        idField.setLabel("设备ID*");
        idField.setType("input");
        idField.setPlaceholder("请输入要删除的设备ID，多个用逗号分隔");
        fields.add(idField);
        
        AgentResponse.FormField confirmField = new AgentResponse.FormField();
        confirmField.setKey("confirm");
        confirmField.setLabel("我已知晓风险并确认删除");
        confirmField.setType("switch");
        fields.add(confirmField);
        
        AgentResponse.FormField reasonField = new AgentResponse.FormField();
        reasonField.setKey("reason");
        reasonField.setLabel("删除原因*");
        reasonField.setType("input");
        reasonField.setPlaceholder("请说明删除原因");
        fields.add(reasonField);
        
        response.setFormFields(fields);
        response.setConfirmText("确认删除");
        response.setCancelText("取消");
        response.setFormId("delete_device_" + System.currentTimeMillis());
        
        return response;
    }

    private AgentResponse handleGeneralQuery(String userMessage, String context) {
        String systemPrompt = "你是系统运维专家助手。你可以帮助用户管理：\n" +
                             "1. 物理机/服务器\n" +
                             "2. 虚拟机\n" +
                             "3. 数据库（MySQL、Redis等）\n" +
                             "4. 中间件（Nginx、Tomcat、Kafka等）\n" +
                             "5. JAR应用\n\n" +
                             "你可以：\n" +
                             "- 查询设备信息和状态\n" +
                             "- 添加新设备\n" +
                             "- 修改设备信息\n" +
                             "- 删除设备（需确认）\n\n" +
                             "请用专业的语言回答，必要时使用表格展示数据。";
        
        String reply = model.generate(systemPrompt + "\n\n对话历史:\n" + context + 
                                     "\n\n用户问题: " + userMessage);
        
        AgentResponse response = new AgentResponse();
        response.setReply(reply);
        response.setType("text");
        return response;
    }

    private AgentResponse createErrorResponse(String errorMsg) {
        AgentResponse response = new AgentResponse();
        response.setReply("❌ " + errorMsg);
        response.setType("text");
        return response;
    }

    private AgentResponse.TableColumn createColumn(String prop, String label, Integer width) {
        AgentResponse.TableColumn column = new AgentResponse.TableColumn();
        column.setProp(prop);
        column.setLabel(label);
        column.setWidth(width);
        return column;
    }

    private AgentResponse.Option createOption(String label, String value) {
        AgentResponse.Option option = new AgentResponse.Option();
        option.setLabel(label);
        option.setValue(value);
        return option;
    }

    enum OperationType {
        QUERY_PHYSICAL,
        QUERY_DATABASE,
        QUERY_MIDDLEWARE,
        QUERY_VIRTUAL,
        QUERY_JARS,
        ADD_DEVICE,
        UPDATE_DEVICE,
        DELETE_DEVICE,
        GENERAL_QUERY
    }
}
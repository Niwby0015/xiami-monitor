package com.ruoyi.web.controller.monitor;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;

@RestController
@RequestMapping("/monitor/inject")
public class InjectController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(InjectController.class);

    @PostMapping("/run")
    public AjaxResult inject(@RequestBody Map<String, Object> params) {
        String type = (String) params.get("type");
        int threads = Integer.parseInt(params.get("threads").toString());
        int time = Integer.parseInt(params.get("time").toString());
        String load = params.get("load") != null ? params.get("load").toString() : "50";

        log.info("开始异常注入: type={}, threads={}, time={}, load={}", type, threads, time, load);

        new Thread(() -> {
            try {
                runInjection(type, threads, time, load);
                log.info("异常注入执行完成: type={}", type);
            } catch (Exception e) {
                log.error("异常注入执行失败: type={}, error={}", type, e.getMessage());
                e.printStackTrace();
            }
        }).start();

        return success("已触发异常注入");
    }

    public void runInjection(String type, int threads, int time, String load) throws Exception {
        String cmd = "";

        if ("qps".equals(type)) {
            cmd = "sysbench oltp_read_only " +
                  "--mysql-host=192.168.31.34 " +
                  "--mysql-port=3307 " +
                  "--mysql-user=test " +
                  "--mysql-password= " +
                  "--mysql-db=test " +
                  "--threads=" + threads + " " +
                  "--time=" + time + " run";
            log.info("执行QPS注入: {}", cmd);
        } else if ("conn".equals(type)) {
            cmd = "sysbench oltp_read_write " +
                  "--mysql-host=192.168.31.34 " +
                  "--mysql-port=3307 " +
                  "--mysql-user=test " +
                  "--mysql-password= " +
                  "--mysql-db=test " +
                  "--threads=" + threads + " " +
                  "--time=" + time + " run";
            log.info("执行连接打满注入: {}", cmd);
        } else if ("slow".equals(type)) {
            cmd = "sysbench /root/WYB/slow_query.lua " +
                  "--mysql-host=192.168.31.34 " +
                  "--mysql-port=3307 " +
                  "--mysql-user=test " +
                  "--mysql-password= " +
                  "--mysql-db=test " +
                  "--threads=100 " +
                  "--time=" + time + " run";
            log.info("执行慢查询注入: {}", cmd);
        } else if ("cpu".equals(type)) {
            cmd = "stress --cpu " + threads + " --timeout " + time;
            log.info("执行CPU注入: {}", cmd);
        } else if ("memory".equals(type)) {
            cmd = "stress --vm " + threads + " --vm-bytes 100MB --timeout " + time;
            log.info("执行内存注入: {}", cmd);
        } else if ("diskio".equals(type)) {
            cmd = "stress --io " + threads + " --timeout " + time;
            log.info("执行磁盘IO注入: {}", cmd);
        } else if ("tablescan".equals(type)) {
            cmd = "mysql -uroot -p -e \"SELECT * FROM large_table;\" && sleep " + time;
            log.info("执行全表扫描注入: {}", cmd);
        } else if ("deadlock".equals(type)) {
            cmd = "mysql -uroot -p -e \"START TRANSACTION; UPDATE test.table1 SET col1 = 'test' WHERE id = 1; SELECT SLEEP(" + time + "); COMMIT;\"";
            log.info("执行死锁注入: {}", cmd);
        } else {
            log.warn("不支持的异常类型: {}", type);
            throw new Exception("不支持的异常类型: " + type);
        }

        executeSSH(cmd);
    }

    public void executeSSH(String command) throws Exception {
        com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();
        com.jcraft.jsch.Session session = jsch.getSession("root", "192.168.31.34", 22);
        session.setPassword("");
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("ConnectTimeout", "30000");
        session.setConfig("ServerAliveInterval", "10000");
        
        log.info("尝试连接SSH服务器: root@192.168.31.34:22");
        
        try {
            session.connect(30000);
            log.info("SSH连接成功: {}", session.isConnected());
        } catch (Exception e) {
            log.error("SSH连接失败: " + e.getMessage());
            log.error("可能的原因: 1. SSH服务未运行 2. 防火墙阻止 3. 网络不通");
            throw e;
        }

        com.jcraft.jsch.ChannelExec channel = (com.jcraft.jsch.ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);
        channel.setErrStream(System.err);
        
        try {
            channel.connect(10000);
            log.info("SSH通道连接成功: {}", channel.isConnected());
        } catch (Exception e) {
            log.error("SSH通道连接失败: " + e.getMessage());
            throw e;
        }

        log.info("SSH命令执行中: {}", command);

        java.io.InputStream in = channel.getInputStream();
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in));
        String line;
        while ((line = reader.readLine()) != null) {
            log.info("SSH输出: {}", line);
        }

        reader.close();
        in.close();

        while (!channel.isClosed()) {
            Thread.sleep(100);
        }

        channel.disconnect();
        session.disconnect();
        
        log.info("SSH命令执行完成: {}", command);
    }
}

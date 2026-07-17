package com.example.aichat.config;

import com.example.aichat.config.props.ChromaDbProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

/**
 * Spring Boot 启动时自动检测并拉起 ChromaDB。
 * 关机时自动销毁子进程。
 */
@Component
public class ChromaDBLauncher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChromaDBLauncher.class);

    private final RestTemplate restTemplate;
    private final ChromaDbProperties chromaDbProperties;
    private final String chromaUrl;
    private final int chromaPort;

    private Process chromaProcess;

    public ChromaDBLauncher(RestTemplate restTemplate,
                             ChromaDbProperties chromaDbProperties) {
        this.restTemplate = restTemplate;
        this.chromaDbProperties = chromaDbProperties;
        this.chromaUrl = chromaDbProperties.getUrl();
        this.chromaPort = extractPort(chromaUrl);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isChromaAlive()) {
            log.info("ChromaDB 已在线: {}", chromaUrl);
            return;
        }

        log.info("ChromaDB 未运行，正在启动... (port={}, data=./chroma_data)", chromaPort);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "chroma", "run",
                    "--path", "./chroma_data",
                    "--host", "0.0.0.0",
                    "--port", String.valueOf(chromaPort)
            );
            pb.directory(new File("."));
            pb.redirectErrorStream(true);
            // 丢弃 stdout，避免阻塞管道
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            chromaProcess = pb.start();

            // 等待 ChromaDB 就绪，最长 15 秒
            if (!waitForReady(15_000)) {
                log.error("ChromaDB 启动超时 ({}ms)，请检查 chroma 命令是否可用", 15_000);
                chromaProcess.destroy();
                chromaProcess = null;
            } else {
                log.info("ChromaDB 启动成功: {}", chromaUrl);
            }
        } catch (Exception e) {
            log.error("启动 ChromaDB 失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        if (chromaProcess != null && chromaProcess.isAlive()) {
            log.info("正在关闭 ChromaDB 子进程...");
            chromaProcess.destroy();
            try {
                chromaProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                chromaProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            log.info("ChromaDB 已关闭");
        }
    }

    private boolean isChromaAlive() {
        try {
            restTemplate.getForEntity(new URI(chromaUrl + "/api/v2/heartbeat"), String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean waitForReady(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (chromaProcess != null && !chromaProcess.isAlive()) {
                return false;
            }
            if (isChromaAlive()) {
                return true;
            }
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static int extractPort(String url) {
        try {
            URI uri = new URI(url);
            int p = uri.getPort();
            return p > 0 ? p : 8000;
        } catch (Exception e) {
            return 8000;
        }
    }
}

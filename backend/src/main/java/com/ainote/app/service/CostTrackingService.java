package com.ainote.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * API 成本追踪服务
 * 根据模型名和 token 使用量计算费用（元）
 */
@Service
@ConfigurationProperties(prefix = "app.cost")
public class CostTrackingService {

    private static final Logger log = LoggerFactory.getLogger(CostTrackingService.class);

    /** 模型单价配置（元/千tokens），从 application.yml 注入 */
    private Map<String, ModelPrice> prices = new HashMap<>();

    public static class ModelPrice {
        private double input;   // 元/千 input tokens
        private double output;  // 元/千 output tokens

        public double getInput() { return input; }
        public void setInput(double input) { this.input = input; }
        public double getOutput() { return output; }
        public void setOutput(double output) { this.output = output; }
    }

    public Map<String, ModelPrice> getPrices() { return prices; }
    public void setPrices(Map<String, ModelPrice> prices) { this.prices = prices; }

    @PostConstruct
    public void init() {
        log.info("CostTrackingService initialized with {} model prices: {}", prices.size(), prices.keySet());
    }

    /**
     * 计算单次 LLM 调用成本（元）
     * @param modelName 模型名称（需与 prices 配置的 key 匹配）
     * @param inputTokens 输入 token 数
     * @param outputTokens 输出 token 数
     * @return 预估成本（元），未知模型返回 0
     */
    public double calculateCost(String modelName, int inputTokens, int outputTokens) {
        if (modelName == null) return 0;

        // 尝试精确匹配，再尝试包含匹配
        ModelPrice price = prices.get(modelName);
        if (price == null) {
            for (var entry : prices.entrySet()) {
                if (modelName.contains(entry.getKey()) || entry.getKey().contains(modelName)) {
                    price = entry.getValue();
                    break;
                }
            }
        }

        if (price == null) {
            log.debug("No price config for model: {}", modelName);
            return 0;
        }

        return inputTokens / 1000.0 * price.input + outputTokens / 1000.0 * price.output;
    }
}

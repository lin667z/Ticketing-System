package org.ticketing_system.biz.aiservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AI 服务应用启动器
 *
 * <p>Phase 2c 变更：移除 {@code @EnableFeignClients} 注解，
 * Feign 阻塞调用已由 WebClient 非阻塞方式替代</p>
 */
@SpringBootApplication(exclude = {
        OpenAiAudioSpeechAutoConfiguration.class,        // 语音合成 (TTS)
        OpenAiAudioTranscriptionAutoConfiguration.class,  // 语音识别 (Whisper)
        OpenAiImageAutoConfiguration.class              // 图像生成 (DALL-E)
}, excludeName = {
        "org.ticketing_system.frameworks.starter.user.config.UserAutoConfiguration"
})
@ConfigurationPropertiesScan
@MapperScan("org.ticketing_system.biz.aiservice.dao.mapper")
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}

package com.jenerator.worker;

import com.jenerator.worker.config.BrowserAutomationProperties;
import com.jenerator.worker.config.ImageProperties;
import com.jenerator.worker.config.NarrationProperties;
import com.jenerator.worker.config.ResearchProperties;
import com.jenerator.worker.config.ScriptGenerationProperties;
import com.jenerator.worker.config.WorkerProperties;
import com.jenerator.worker.config.YoutubeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({
        WorkerProperties.class,
        YoutubeProperties.class,
        NarrationProperties.class,
        ImageProperties.class,
        ResearchProperties.class,
        ScriptGenerationProperties.class,
        BrowserAutomationProperties.class
})
public class WorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}

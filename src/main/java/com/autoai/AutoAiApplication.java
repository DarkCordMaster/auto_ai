package com.autoai;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class AutoAiApplication {
    public static void main(String[] args) {
        // GUI(탐색기)를 띄우기 위해 headless 모드를 false로 설정
        new SpringApplicationBuilder(AutoAiApplication.class)
                .headless(false)
                .run(args);
    }
}

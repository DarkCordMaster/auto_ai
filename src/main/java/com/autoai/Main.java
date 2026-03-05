package com.autoai;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Error: GEMINI_API_KEY is not set.");
            return;
        }

        GeminiAgent agent = new GeminiAgent(apiKey);
        ExcelGenerator generator = new ExcelGenerator();

        File srcDir = new File("src/main/java/com/autoai");
        List<File> javaFiles = new ArrayList<>();
        findJavaFiles(srcDir, javaFiles);

        System.out.println("Found " + javaFiles.size() + " Java files. Starting analysis...");

        StringBuilder allAnalysis = new StringBuilder("[");

        for (File file : javaFiles) {
            try {
                if (file.getName().equals("Main.java")) continue;

                System.out.println("Analyzing: " + file.getName() + " ...");
                String content = Files.readString(file.toPath());
                String analysisResult = agent.analyzeCode(file.getName(), content, false);
                
                String trimmedResult = analysisResult.trim();
                if (trimmedResult.startsWith("[")) {
                    trimmedResult = trimmedResult.substring(1, trimmedResult.length() - 1);
                }
                
                if (allAnalysis.length() > 1 && !trimmedResult.isEmpty()) {
                    allAnalysis.append(",");
                }
                allAnalysis.append(trimmedResult);

                // 무료 티어 요청 제한(Rate Limit)을 피하기 위해 2초간 대기
                System.out.println("Waiting for next request...");
                Thread.sleep(2000);
                
            } catch (Exception e) {
                System.err.println("Failed to analyze " + file.getName() + ": " + e.getMessage());
            }
        }
        allAnalysis.append("]");

        if (allAnalysis.toString().equals("[]")) {
            System.err.println("No analysis data produced. Check API limits.");
            return;
        }

        try {
            String outputPath = "Code_Documentation.xlsx";
            generator.generate(allAnalysis.toString(), outputPath);
            System.out.println("\n🎉 Success! Documentation generated at: " + new File(outputPath).getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to generate Excel: " + e.getMessage());
        }
    }

    private static void findJavaFiles(File dir, List<File> fileList) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findJavaFiles(file, fileList);
                } else if (file.getName().endsWith(".java")) {
                    fileList.add(file);
                }
            }
        }
    }
}

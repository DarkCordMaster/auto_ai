package com.autoai;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class GeminiAgent {
    private final String apiKey;
    private final OkHttpClient client;
    
    // 사용자 요청 순서에 따른 모델 배치 (최신 및 성능 위주)
    private final String[] MODEL_CANDIDATES = {
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-2.0-flash-lite-preview-02-05",
        "gemini-flash-latest"
    };

    public GeminiAgent(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(180, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .build();
    }

    public String analyzeCode(String fileName, String codeContent, boolean isFullPower) throws IOException {
        String cleanedCode = minifyCode(codeContent, isFullPower);
        String prompt = loadPrompt("api_analysis") + "\n\n### 소스코드 ###\n" + cleanedCode;
        return executeMultiModelRequest(prompt, true);
    }

    public String generateProjectSummary(String codeContent, boolean isFullPower) throws IOException {
        String cleanedCode = minifyCode(codeContent, isFullPower);
        String prompt = loadPrompt("project_summary") + "\n\n### 분석할 소스코드 ###\n" + cleanedCode;
        return executeMultiModelRequest(prompt, true);
    }

    public String analyzeForNotion(String serviceDesc, String planning, String period, String codeContent, boolean isFullPower) throws IOException {
        String cleanedCode = minifyCode(codeContent, isFullPower);
        String prompt = loadPrompt("notion_analysis") + "\n\n" +
                "### 사용자 데이터 ###\n" +
                "- 서비스 설명: " + serviceDesc + "\n" +
                "- 기획 설계: " + planning + "\n" +
                "- 개발 기간: " + period + "\n\n" +
                "### 분석할 코드 ###\n" + cleanedCode;

        return executeMultiModelRequest(prompt, false);
    }

    public static boolean validateKey(String key) {
        OkHttpClient tempClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        
        // 1. 모델 리스트 조회로 키 유효성 우선 체크 (가장 가볍고 확실한 방법)
        String listUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=" + key;
        Request listRequest = new Request.Builder().url(listUrl).get().build();
        
        try (Response response = tempClient.newCall(listRequest).execute()) {
            if (response.isSuccessful()) {
                System.out.println("✅ Key validated via Model List API.");
                return true;
            }
            System.err.println("⚠️ Model List API failed: HTTP " + response.code());
        } catch (Exception e) {
            System.err.println("⚠️ Connection error during key validation: " + e.getMessage());
        }

        // 2. 실패 시 기존 방식(컨텐츠 생성)으로 한 번 더 시도
        String[] testModels = {"gemini-1.5-flash", "gemini-2.0-flash"};
        String jsonPayload = "{\"contents\":[{\"parts\":[{\"text\":\"1\"}]}]}";
        RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json; charset=utf-8"));

        for (String model : testModels) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key;
            Request request = new Request.Builder().url(url).post(body).build();
            
            try (Response response = tempClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    System.out.println("✅ Key validated with " + model);
                    return true;
                }
                System.err.println("⚠️ " + model + " validation failed: HTTP " + response.code());
            } catch (Exception e) {
                System.err.println("⚠️ Error validating with " + model + ": " + e.getMessage());
            }
        }
        
        System.err.println("❌ All key validation attempts failed.");
        return false;
    }

    private String executeMultiModelRequest(String prompt, boolean isJsonOnly) throws IOException {
        JSONObject requestBody = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject part = new JSONObject();
        part.put("text", prompt);
        parts.put(part);
        content.put("parts", parts);
        contents.put(content);
        requestBody.put("contents", contents);

        String jsonPayload = requestBody.toString();

        for (String modelName : MODEL_CANDIDATES) {
            try {
                System.out.println("🤖 Trying model: " + modelName + "...");
                String result = executeWithRetry(modelName, jsonPayload, isJsonOnly);
                if (result != null && !result.isEmpty() && !result.equals("[]")) return result;
            } catch (IOException e) {
                System.err.println("⚠️ Model " + modelName + " failed: " + e.getMessage());
                continue;
            }
        }
        throw new IOException("모든 모델의 요청이 실패했습니다.");
    }

    private String executeWithRetry(String modelName, String jsonPayload, boolean isJsonOnly) throws IOException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;
        int maxRetries = 2;
        int retryCount = 0;

        while (true) {
            RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder().url(url).post(body).build();

            try (Response response = client.newCall(request).execute()) {
                String responseData = response.body() != null ? response.body().string() : "{}";
                
                if (response.isSuccessful()) {
                    JSONObject jsonResponse = new JSONObject(responseData);
                    String rawText = jsonResponse.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
                    
                    if (isJsonOnly) {
                        int start = rawText.indexOf(rawText.contains("[") ? "[" : "{");
                        int end = rawText.lastIndexOf(rawText.contains("]") ? "]" : "}");
                        return (start != -1 && end != -1) ? rawText.substring(start, end + 1) : "[]";
                    }
                    return rawText;
                }

                // 429(횟수제한) 또는 503(서버과부하) 시 재시도
                if ((response.code() == 429 || response.code() == 503) && retryCount < maxRetries) {
                    retryCount++;
                    long waitTime = response.code() == 429 ? 5000 : 2000;
                    System.out.println("⏳ API " + response.code() + " error. Retrying in " + (waitTime/1000) + "s... (" + retryCount + "/" + maxRetries + ")");
                    Thread.sleep(waitTime);
                    continue;
                }
                
                throw new IOException("HTTP " + response.code() + ": " + responseData);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Retry interrupted", e);
            }
        }
    }

    public String generateTestCases(String codeContent, boolean isFullPower) throws IOException {
        String cleanedCode = minifyCode(codeContent, isFullPower);
        String prompt = loadPrompt("testcase_analysis") + "\n\n### 분석할 소스코드 ###\n" + cleanedCode;
        return executeMultiModelRequest(prompt, true);
    }

    private String loadPrompt(String key) {
        try {
            String content = java.nio.file.Files.readString(java.nio.file.Paths.get("prompts", "prompts.json"), java.nio.charset.StandardCharsets.UTF_8);
            return new JSONObject(content).optString(key, "");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to load prompt for key: " + key);
            return ""; 
        }
    }

    private String minifyCode(String code, boolean isFullPower) {
        if (code == null || code.isEmpty()) return "";
        
        // 1. 주석 제거
        String minified = code.replaceAll("//.*", "")
                             .replaceAll("/\\*([\\s\\S]*?)\\*/", "");
        
        if (isFullPower) {
            // 풀파워 모드: 공백만 압축하고 내용은 보존
            return minified.replaceAll("\\s+", " ").trim();
        }

        // 2. 절약 모드: 메서드 바디 제거 핵심 로직
        StringBuilder sb = new StringBuilder();
        String[] lines = minified.split("\n");
        boolean inMethod = false;
        int braceCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (!inMethod) {
                sb.append(trimmed).append(" ");
                if (trimmed.contains("{") && !trimmed.contains("class") && !trimmed.contains("interface") && !trimmed.contains("enum")) {
                    inMethod = true;
                    braceCount = 1;
                    sb.append("/* body omitted */ } ");
                }
            } else {
                if (trimmed.contains("{")) braceCount++;
                if (trimmed.contains("}")) braceCount--;
                if (braceCount <= 0) inMethod = false;
            }
        }

        return sb.toString().replaceAll("\\s+", " ").trim();
    }
}

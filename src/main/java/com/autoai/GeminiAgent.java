package com.autoai;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class GeminiAgent {
    private final String apiKey;
    private final OkHttpClient client;
    
    private final String[] MODEL_CANDIDATES = {
        "gemini-flash-latest",
        "gemini-2.0-flash-lite",
        "gemini-pro-latest",
        "gemini-1.5-flash",
        "gemini-2.0-flash"
    };

    public GeminiAgent(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(180, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .build();
    }

    public String analyzeCode(String fileName, String codeContent) throws IOException {
        String cleanedCode = minifyCode(codeContent);
        
        String prompt = "당신은 전문 Java 백엔드 개발자입니다. 제공된 소스코드를 분석하여 상세한 API 명세서를 JSON 배열 형식으로 작성하세요.\n\n" +
                "### 반드시 다음 JSON 구조를 지키세요 ###\n" +
                "[\n" +
                "  {\n" +
                "    \"title\": \"기능 명칭\",\n" +
                "    \"httpMethod\": \"GET/POST 등\",\n" +
                "    \"apiPath\": \"/api/path\",\n" +
                "    \"description\": \"설명\",\n" +
                "    \"reqHeaders\": [ { \"key\": \"Content-Type\", \"value\": \"application/json\" } ],\n" +
                "    \"reqParams\": [ { \"name\": \"id\", \"value\": \"val\", \"type\": \"String\", \"required\": \"Y\", \"desc\": \"설명\" } ],\n" +
                "    \"reqBody\": [ { \"type\": \"JSON\", \"value\": \"{\\n  \\\"key\\\": \\\"value\\\"\\n}\" } ],\n" +
                "    \"resHeaders\": [],\n" +
                "    \"resBody\": [ { \"code\": \"200\", \"desc\": \"성공\" } ]\n" +
                "  }\n" +
                "]\n\n" +
                "### 중요 지침 ###\n" +
                "1. reqBody의 'value' 필드에 들어가는 JSON 샘플 데이터는 반드시 줄바꿈(\\n)과 들여쓰기를 포함한 Pretty-print 형식으로 작성하세요.\n" +
                "2. 데이터가 한 줄로 길게 늘어지지 않게 가독성을 최우선으로 하세요.\n" +
                "3. 응답 결과는 반드시 JSON 배열만 출력하세요.\n\n" +
                "### 소스코드 ###\n" + cleanedCode;

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
                System.out.println("🤖 Analyzing with model: " + modelName + "...");
                String result = executeRequest(modelName, jsonPayload);
                if (result != null && !result.equals("[]")) {
                    return result;
                }
            } catch (IOException e) {
                if (e.getMessage().contains("404") || e.getMessage().contains("429")) continue;
                throw e;
            }
        }
        throw new IOException("분석에 실패했습니다.");
    }

    private String executeRequest(String modelName, String jsonPayload) throws IOException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;
        RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();
        return executeWithRetry(request, 1);
    }

    private String executeWithRetry(Request request, int maxRetries) throws IOException {
        int retryCount = 0;
        while (true) {
            try (Response response = client.newCall(request).execute()) {
                String responseData = response.body() != null ? response.body().string() : "{}";
                if (response.isSuccessful()) {
                    JSONObject jsonResponse = new JSONObject(responseData);
                    String rawText = jsonResponse.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
                    int start = rawText.indexOf("[");
                    int end = rawText.lastIndexOf("]");
                    return (start != -1 && end != -1) ? rawText.substring(start, end + 1) : "[]";
                }
                if (response.code() == 429 && retryCount < maxRetries) {
                    retryCount++;
                    Thread.sleep(5000);
                    continue;
                }
                throw new IOException("API Error: " + response.code() + " | " + responseData);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Retry interrupted", e);
            }
        }
    }

    private String minifyCode(String code) {
        if (code == null) return "";
        return code.replaceAll("//.*", "").replaceAll("/\\*([\\s\\S]*?)\\*/", "").replaceAll("\\s+", " ").trim();
    }
}

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
        
        // 프롬프트를 ExcelGenerator가 기대하는 JSON 구조에 맞춰 구체화
        String prompt = "당신은 전문 Java 백엔드 개발자입니다. 제공된 소스코드를 분석하여 상세한 API 명세서를 JSON 배열 형식으로 작성하세요.\n\n" +
                "### 반드시 다음 JSON 구조를 지키세요 ###\n" +
                "[\n" +
                "  {\n" +
                "    \"title\": \"기능 명칭 (예: 회원 가입)\",\n" +
                "    \"httpMethod\": \"GET/POST/PUT/DELETE 등\",\n" +
                "    \"apiPath\": \"/api/v1/user/join\",\n" +
                "    \"description\": \"기능에 대한 상세 설명\",\n" +
                "    \"reqHeaders\": [ { \"key\": \"Content-Type\", \"value\": \"application/json\" } ],\n" +
                "    \"reqParams\": [ { \"name\": \"id\", \"value\": \"sample_id\", \"type\": \"String\", \"required\": \"Y/N\", \"desc\": \"설명\" } ],\n" +
                "    \"reqBody\": [ { \"type\": \"JSON\", \"value\": \"{\\\"name\\\": \\\"홍길동\\\"}\" } ],\n" +
                "    \"resHeaders\": [],\n" +
                "    \"resBody\": [ { \"code\": \"200\", \"desc\": \"성공 설명\" }, { \"code\": \"400\", \"desc\": \"에러 설명\" } ]\n" +
                "  }\n" +
                "]\n\n" +
                "### 분석 지침 ###\n" +
                "1. @RequestMapping, @GetMapping 등의 어노테이션을 통해 경로와 메소드를 정확히 추출하세요.\n" +
                "2. @RequestParam, @PathVariable, @RequestBody 등을 분석하여 파라미터와 바디 정보를 상세히 기술하세요.\n" +
                "3. 서비스 로직이나 주석을 참고하여 에러 코드(400, 401, 500 등)와 성공 응답을 최대한 추론하여 작성하세요.\n" +
                "4. 데이터가 없는 필드는 빈 배열([])로 두지 말고, 예시 데이터나 '없음'을 넣어 실질적인 명세서가 되게 하세요.\n" +
                "5. 응답 결과는 반드시 JSON 배열만 출력하세요. 설명글은 생략하세요.\n\n" +
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

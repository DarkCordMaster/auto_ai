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

        return executeMultiModelRequest(prompt, true);
    }

    public String generateProjectSummary(String codeContent, boolean isFullPower) throws IOException {
        String cleanedCode = minifyCode(codeContent, isFullPower);
        
        String prompt = "당신은 전문 프로젝트 매니저이자 아키텍트입니다. 제공된 소스코드를 분석하여 다음 두 항목을 작성하세요.\n\n" +
                "1. **서비스 설명**: 이 프로젝트가 무엇을 위한 서비스인지 3-4문장으로 요약.\n" +
                "2. **기획 및 설계**: 주요 핵심 기능과 시스템의 설계 의도, 아키텍처 특징 요약.\n\n" +
                "### 반드시 다음 JSON 형식을 지키세요 ###\n" +
                "{\n" +
                "  \"description\": \"서비스 설명 내용\",\n" +
                "  \"planning\": \"기획 및 설계 내용\"\n" +
                "}\n\n" +
                "### 분석할 소스코드 ###\n" + cleanedCode;

        return executeMultiModelRequest(prompt, true);
    }

    public String analyzeForNotion(String serviceDesc, String planning, String period, String codeContent, boolean isFullPower) throws IOException {
        String cleanedCode = minifyCode(codeContent, isFullPower);
        
        String prompt = "당신은 전문 소프트웨어 아키텍트입니다. 다음 지침에 따라 Notion에 최적화된 마크다운 문서를 작성하세요.\n\n" +
                "### 반드시 지켜야 할 레이아웃 규칙 ###\n" +
                "1. **프로젝트 제목 (파일명)**: 가장 상단에 `# 🚀 [프로젝트명]` 형식을 사용하고 바로 아래 가로선 `---`을 넣으세요.\n" +
                "2. **메인 섹션 제목**: 모든 대제목(제목 1)은 아이콘을 포함하고 아래에 가로선 `---`을 넣으세요.\n" +
                "   - 예: `# 👩‍🏫 웹 서비스 설명`, `# 👩‍💻 기획 & 설계`, `# 🗓 개발 기간`\n" +
                "3. **접기(Toggle)**: 제목과 가로선 바로 아래에 `<details><summary>상세 내용 보기</summary>`를 사용하여 내용을 감싸세요.\n" +
                "4. **섹션 종료**: 각 큰 섹션(details 태그 종료 후)이 끝날 때마다 가로선 `---`을 넣어 구분하세요.\n" +
                "5. **소제목**: 소제목은 **굵은 글씨**만 사용하거나 HTML 태그를 활용해 크게 표시하고 아래에 짧은 선을 넣으세요.\n" +
                "6. **분류 금지**: 현재 분석 중인 프로젝트의 실제 기능 단위로만 자연스럽게 설명하세요.\n" +
                "7. **글머리 기호**: 모든 문장은 `-`로 시작하고 단락 사이 여백을 충분히 두세요.\n\n" +
                "### 사용자 데이터 ###\n" +
                "- 서비스 설명: " + serviceDesc + "\n" +
                "- 기획 설계: " + planning + "\n" +
                "- 개발 기간: " + period + "\n\n" +
                "### 분석할 코드 ###\n" + cleanedCode;

        return executeMultiModelRequest(prompt, false);
    }

    public static boolean validateKey(String key) {
        OkHttpClient tempClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + key;
        String jsonPayload = "{\"contents\":[{\"parts\":[{\"text\":\"ping\"}]}]}";
        RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();

        try (Response response = tempClient.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (Exception e) {
            return false;
        }
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

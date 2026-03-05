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

    public String generateProjectSummary(String codeContent) throws IOException {
        String cleanedCode = minifyCode(codeContent);
        
        String prompt = "당신은 전문 프로젝트 매니저이자 아키텍트입니다. 제공된 소스코드를 분석하여 다음 두 항목을 작성하세요.\n\n" +
                "1. **서비스 설명**: 이 프로젝트가 무엇을 위한 서비스인지 3-4문장으로 요약.\n" +
                "2. **기획 및 설계**: 주요 핵심 기능과 시스템의 설계 의도, 아키텍처 특징 요약.\n\n" +
                "### 반드시 다음 JSON 형식을 지키세요 ###\n" +
                "{\n" +
                "  \"description\": \"서비스 설명 내용\",\n" +
                "  \"planning\": \"기획 및 설계 내용\"\n" +
                "}\n\n" +
                "### 분석할 소스코드 ###\n" + cleanedCode;

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
                System.out.println("🤖 Generating Summary with: " + modelName + "...");
                String result = executeSummaryRequest(modelName, jsonPayload);
                if (result != null && !result.isEmpty()) return result;
            } catch (IOException e) {
                if (e.getMessage().contains("404") || e.getMessage().contains("429")) continue;
                throw e;
            }
        }
        throw new IOException("요약 생성에 실패했습니다.");
    }

    private String executeSummaryRequest(String modelName, String jsonPayload) throws IOException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;
        RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseData = response.body() != null ? response.body().string() : "{}";
            if (response.isSuccessful()) {
                JSONObject jsonResponse = new JSONObject(responseData);
                String rawText = jsonResponse.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
                // JSON만 추출
                int start = rawText.indexOf("{");
                int end = rawText.lastIndexOf("}");
                return (start != -1 && end != -1) ? rawText.substring(start, end + 1) : "{}";
            }
            throw new IOException("API Error: " + response.code());
        }
    }

    public String analyzeForNotion(String serviceDesc, String planning, String period, String codeContent) throws IOException {
        String cleanedCode = minifyCode(codeContent);
        
        String prompt = "당신은 전문 소프트웨어 아키텍트입니다. 다음 지침에 따라 Notion에 최적화된 마크다운 문서를 작성하세요.\n\n" +
                "### 반드시 지켜야 할 레이아웃 규칙 ###\n" +
                "1. **프로젝트 제목 (파일명)**: 가장 상단에 `# 🚀 [프로젝트명]` 형식을 사용하고 바로 아래 가로선 `---`을 넣으세요.\n" +
                "2. **메인 섹션 제목**: 모든 대제목(제목 1)은 아이콘을 포함하고 아래에 가로선 `---`을 넣으세요.\n" +
                "   - 예: `# 👩‍🏫 웹 서비스 설명`, `# 👩‍💻 기획 & 설계`, `# 🗓 개발 기간`\n" +
                "3. **접기(Toggle)**: 제목과 가로선 바로 아래에 `<details><summary>상세 내용 보기</summary>`를 사용하여 내용을 감싸세요.\n" +
                "4. **섹션 종료**: 각 큰 섹션(details 태그 종료 후)이 끝날 때마다 가로선 `---`을 넣어 구분하세요.\n" +
                "5. **소제목**: `###` 같은 마크다운 기호가 직접 노출되지 않게 하세요. 소제목은 **굵은 글씨**만 사용하거나 HTML 태그를 활용해 크게 표시하고 아래에 짧은 선을 넣으세요.\n" +
                "6. **분류 금지**: 메이커, 관리자 등으로 억지로 나누지 말고, 현재 분석 중인 프로젝트의 실제 기능 단위로만 자연스럽게 설명하세요.\n" +
                "7. **글머리 기호**: 모든 문장은 `-`로 시작하고 단락 사이 여백을 충분히 두세요.\n\n" +
                "### 문서 구조 예시 ###\n" +
                "# 🚀 [프로젝트명]\n" +
                "---\n\n" +
                "# 👩‍🏫 웹 서비스 설명\n" +
                "---\n" +
                "<details>\n" +
                "<summary>상세 내용 보기</summary>\n\n" +
                "- 서비스 내용...\n" +
                "</details>\n" +
                "---\n\n" +
                "### 사용자 데이터 ###\n" +
                "- 서비스 설명: " + serviceDesc + "\n" +
                "- 기획 설계: " + planning + "\n" +
                "- 개발 기간: " + period + "\n\n" +
                "### 분석할 코드 ###\n" + cleanedCode;

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
                System.out.println("🤖 Notion Analysis with: " + modelName + "...");
                String result = executeNotionRequest(modelName, jsonPayload);
                if (result != null && !result.isEmpty()) return result;
            } catch (IOException e) {
                if (e.getMessage().contains("404") || e.getMessage().contains("429")) continue;
                throw e;
            }
        }
        throw new IOException("분석에 실패했습니다.");
    }

    private String executeNotionRequest(String modelName, String jsonPayload) throws IOException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;
        RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseData = response.body() != null ? response.body().string() : "{}";
            if (response.isSuccessful()) {
                JSONObject jsonResponse = new JSONObject(responseData);
                return jsonResponse.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
            }
            throw new IOException("API Error: " + response.code());
        }
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

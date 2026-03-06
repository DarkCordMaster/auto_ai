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
        
        String prompt = "당신은 전문 소프트웨어 아키텍트입니다. 다음 지침에 따라 Notion에 최적화된 차분하고 전문적인 마크다운 문서를 작성하세요.\n\n" +
                "### 반드시 지켜야 할 레이아웃 규칙 ###\n" +
                "1. **상단 커버 (Cover)**: 문서 가장 첫 줄에 다음 단색 이미지 주소를 삽입하여 노션의 기본 단색 커버 효과를 주세요: `![Cover](https://www.colorbook.io/image/hex/1e293b.png)`\n" +
                "   - 이미지 주소 다음 줄은 반드시 한 줄 비워두세요.\n" +
                "2. **최상위 제목 (H1)**: `# 📂 [프로젝트명]`\n" +
                "   - **중요**: 제목 바로 다음 줄에 `---` (가로 실선)을 넣어 섹션을 구분하세요.\n" +
                "3. **메인 섹션 (H2)**: `## [아이콘] [섹션명]`\n" +
                "   - 모든 H2 제목 아래에도 반드시 `---`를 넣어 가독성을 높이세요.\n" +
                "4. **접기 기능 (Toggle)**: 실선 아래에 `<details><summary>상세 내용 보기</summary>`를 사용하여 내용을 정리하세요.\n" +
                "5. **본문 스타일**: 핵심 기술이나 중요 단어는 `**텍스트**`로 강조하고, 문장은 `-` 불릿 포인트를 사용하여 간결하게 작성하세요.\n" +
                "6. **여백**: 각 섹션 사이에는 충분한 공백(빈 줄)을 두어 노션 블록이 겹쳐 보이지 않게 하세요.\n\n" +
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
        
        String prompt = "당신은 전문 QA 엔지니어입니다. 제공된 소스코드를 분석하여 상세한 테스트 시나리오(Test Case)를 JSON 배열 형식으로 작성하세요.\n\n" +
                "### 반드시 다음 JSON 구조를 지키세요 ###\n" +
                "[\n" +
                "  {\n" +
                "    \"id\": \"TC-01\",\n" +
                "    \"category\": \"정상/예외\",\n" +
                "    \"feature\": \"기능명\",\n" +
                "    \"scenario\": \"테스트 시나리오 설명\",\n" +
                "    \"inputData\": \"입력 데이터 샘플 (JSON 또는 텍스트)\",\n" +
                "    \"expectedResult\": \"AI가 추론한 기대 결과 내용\"\n" +
                "  }\n" +
                "]\n\n" +
                "### 중요 지침 ###\n" +
                "1. 정상적인 흐름뿐만 아니라, 필수값 누락, 조건 미달 등 발생 가능한 예외 상황을 최대한 상세히 추출하세요.\n" +
                "2. 'expectedResult'는 코드의 로직(if문, 유효성 검사 등)을 바탕으로 정확하게 추론하세요.\n" +
                "3. 응답은 반드시 순수 JSON 배열만 출력하세요.\n\n" +
                "### 분석할 소스코드 ###\n" + cleanedCode;

        return executeMultiModelRequest(prompt, true);
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

package com.autoai;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/doc")
public class DocController {

    private final String apiKey = System.getenv("GEMINI_API_KEY");

    @PostMapping("/analyze-integrated")
    public ResponseEntity<Resource> analyzeIntegrated(
            @RequestParam(value = "controller", required = false) List<MultipartFile> controllers,
            @RequestParam(value = "service", required = false) List<MultipartFile> services,
            @RequestParam(value = "dto", required = false) List<MultipartFile> dtos,
            @RequestParam(value = "mapper", required = false) List<MultipartFile> mappers,
            @RequestParam(value = "fileName", required = false) String customFileName
    ) throws Exception {
        
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("❌ ERROR: GEMINI_API_KEY environment variable is NOT SET!");
            throw new Exception("GEMINI_API_KEY is not set.");
        } else {
            System.out.println("✅ GEMINI_API_KEY loaded: " + apiKey.substring(0, 4) + "****");
        }

        StringBuilder context = new StringBuilder();
        context.append("### PROJECT STRUCTURE ANALYSIS REQUEST ###\n\n");

        appendFiles(context, "CONTROLLER FILES (Analyze these as entry points)", controllers);
        appendFiles(context, "SERVICE FILES (Business Logic)", services);
        appendFiles(context, "DTO/MODEL FILES (Data Structures)", dtos);
        appendFiles(context, "MAPPER/XML/OTHER FILES", mappers);

        GeminiAgent agent = new GeminiAgent(apiKey);
        ExcelGenerator generator = new ExcelGenerator();

        System.out.println("Starting integrated analysis...");
        
        // 429 에러 방지를 위해 요청 전 2초 대기 (안전장치)
        Thread.sleep(2000);
        
        String analysisResult = agent.analyzeCode("Integrated Analysis", context.toString());

        // 분석 결과가 비었거나 에러일 경우 처리
        if (analysisResult == null || analysisResult.equals("[]")) {
            throw new Exception("AI 분석 결과가 비어있습니다. 잠시 후 다시 시도해주세요.");
        }

        // 파일명 결정 (사용자 입력 우선, 없으면 날짜 기준)
        String fileName;
        if (customFileName != null && !customFileName.trim().isEmpty()) {
            fileName = customFileName.trim();
            if (!fileName.endsWith(".xlsx")) fileName += ".xlsx";
        } else {
            String today = java.time.LocalDate.now().toString().replace("-", "");
            fileName = "통합_API_명세서_" + today + ".xlsx";
        }
        
        String outputPath = fileName;
        
        generator.generate(analysisResult, outputPath);

        File excelFile = new File(outputPath);
        Resource resource = new FileSystemResource(excelFile);

        // 한글 파일명 깨짐 방지를 위한 인코딩 처리
        String encodedFileName = java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    private void appendFiles(StringBuilder sb, String title, List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) return;
        sb.append("== ").append(title).append(" ==\n");
        for (MultipartFile file : files) {
            sb.append("[File: ").append(file.getOriginalFilename()).append("]\n");
            sb.append(new String(file.getBytes(), StandardCharsets.UTF_8)).append("\n\n");
        }
    }

    @PostMapping("/analyze")
    public ResponseEntity<Resource> analyze(@RequestParam("files") List<MultipartFile> files) throws Exception {
        return analyzeIntegrated(files, null, null, null, null);
    }
}

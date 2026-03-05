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
    public ResponseEntity<?> analyzeIntegrated(
            @RequestParam(value = "controller", required = false) List<MultipartFile> controllers,
            @RequestParam(value = "service", required = false) List<MultipartFile> services,
            @RequestParam(value = "dto", required = false) List<MultipartFile> dtos,
            @RequestParam(value = "mapper", required = false) List<MultipartFile> mappers,
            @RequestParam(value = "fileName", required = false) String customFileName,
            @RequestParam(value = "projectPath", required = false) String projectPath
    ) {
        try {
            if (customFileName == null || customFileName.trim().isEmpty()) {
                throw new Exception("* 파일명은 필수로 입력해주세요.");
            }

            StringBuilder context = new StringBuilder();
            
            // 로컬 경로가 입력된 경우 해당 폴더에서 직접 읽기
            if (projectPath != null && !projectPath.trim().isEmpty()) {
                File root = new File(projectPath.trim());
                if (!root.exists() || !root.isDirectory()) throw new Exception("* 올바른 폴더 경로가 아닙니다.");
                scanAndAppend(root, context);
            }

            // 업로드된 파일이 있는 경우 추가
            appendFiles(context, "CONTROLLER", controllers);
            appendFiles(context, "SERVICE", services);
            appendFiles(context, "DTO", dtos);
            appendFiles(context, "MAPPER", mappers);

            if (context.toString().trim().isEmpty()) {
                throw new Exception("* 분석할 소스코드가 없습니다. 경로를 입력하거나 파일을 업로드해주세요.");
            }

            GeminiAgent agent = new GeminiAgent(apiKey);
            ExcelGenerator generator = new ExcelGenerator();

            System.out.println("Starting integrated analysis...");
            Thread.sleep(2000);
            
            String analysisResult = agent.analyzeCode("Integrated Analysis", context.toString());

            if (analysisResult == null || analysisResult.equals("[]")) {
                throw new Exception("AI 분석 결과가 비어있습니다. 잠시 후 다시 시도해주세요.");
            }

            String fileName = customFileName.trim();
            if (!fileName.endsWith(".xlsx")) fileName += ".xlsx";
            
            generator.generate(analysisResult, fileName);

            File excelFile = new File(fileName);
            Resource resource = new FileSystemResource(excelFile);
            String encodedFileName = java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private void scanAndAppend(File dir, StringBuilder sb) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanAndAppend(file, sb);
            } else if (file.getName().endsWith(".java") || file.getName().endsWith(".xml")) {
                sb.append("[File: ").append(file.getAbsolutePath()).append("]\n");
                sb.append(java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8)).append("\n\n");
            }
        }
    }

    private void appendFiles(StringBuilder sb, String title, List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) return;
        sb.append("== ").append(title).append(" ==\n");
        for (MultipartFile file : files) {
            sb.append("[File: ").append(file.getOriginalFilename()).append("]\n");
            sb.append(new String(file.getBytes(), StandardCharsets.UTF_8)).append("\n\n");
        }
    }

    @PostMapping("/analyze-notion")
    public ResponseEntity<?> analyzeNotion(
            @RequestParam(value = "controller", required = false) List<MultipartFile> controllers,
            @RequestParam(value = "service", required = false) List<MultipartFile> services,
            @RequestParam(value = "dto", required = false) List<MultipartFile> dtos,
            @RequestParam(value = "mapper", required = false) List<MultipartFile> mappers,
            @RequestParam(value = "fileName", required = false) String customFileName,
            @RequestParam(value = "serviceDesc", required = false) String serviceDesc,
            @RequestParam(value = "planning", required = false) String planning,
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "projectPath", required = false) String projectPath
    ) {
        try {
            if (customFileName == null || customFileName.trim().isEmpty()) {
                throw new Exception("* 파일명은 필수로 입력해주세요.");
            }

            StringBuilder context = new StringBuilder();
            
            if (projectPath != null && !projectPath.trim().isEmpty()) {
                File root = new File(projectPath.trim());
                if (!root.exists() || !root.isDirectory()) throw new Exception("* 올바른 폴더 경로가 아닙니다.");
                scanAndAppend(root, context);
            }

            appendFiles(context, "SOURCE CODE", controllers);
            appendFiles(context, "SOURCE CODE", services);
            appendFiles(context, "SOURCE CODE", dtos);
            appendFiles(context, "SOURCE CODE", mappers);

            GeminiAgent agent = new GeminiAgent(apiKey);
            String markdownResult = agent.analyzeForNotion(serviceDesc, planning, period, context.toString());

            String fileName = customFileName.trim();
            if (!fileName.endsWith(".md")) fileName += ".md";

            byte[] contentBytes = markdownResult.getBytes(StandardCharsets.UTF_8);
            String encodedFileName = java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                    .contentType(MediaType.TEXT_MARKDOWN)
                    .body(contentBytes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/generate-summary")
    public ResponseEntity<?> generateSummary(
            @RequestParam(value = "controller", required = false) List<MultipartFile> controllers,
            @RequestParam(value = "service", required = false) List<MultipartFile> services,
            @RequestParam(value = "dto", required = false) List<MultipartFile> dtos,
            @RequestParam(value = "mapper", required = false) List<MultipartFile> mappers,
            @RequestParam(value = "projectPath", required = false) String projectPath
    ) {
        try {
            StringBuilder context = new StringBuilder();
            
            if (projectPath != null && !projectPath.trim().isEmpty()) {
                File root = new File(projectPath.trim());
                if (root.exists() && root.isDirectory()) scanAndAppend(root, context);
            }

            appendFiles(context, "SOURCE CODE", controllers);
            appendFiles(context, "SOURCE CODE", services);
            appendFiles(context, "SOURCE CODE", dtos);
            appendFiles(context, "SOURCE CODE", mappers);

            if (context.toString().trim().isEmpty()) {
                throw new Exception("* 분석할 소스코드 파일을 먼저 업로드하거나 경로를 입력해주세요.");
            }

            GeminiAgent agent = new GeminiAgent(apiKey);
            String summaryJson = agent.generateProjectSummary(context.toString());

            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(summaryJson);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/scan-project")
    public ResponseEntity<?> scanProject(@RequestParam("path") String path) {
        try {
            File root = new File(path.trim());
            if (!root.exists() || !root.isDirectory()) {
                throw new Exception("* 올바른 폴더 경로가 아닙니다.");
            }

            java.util.Map<String, List<String>> result = new java.util.HashMap<>();
            result.put("controller", new java.util.ArrayList<>());
            result.put("service", new java.util.ArrayList<>());
            result.put("dto", new java.util.ArrayList<>());
            result.put("mapper", new java.util.ArrayList<>());

            performScan(root, result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/open-folder-dialog")
    public ResponseEntity<String> openFolderDialog() {
        try {
            // 시스템 스타일의 탐색기를 띄우기 위함
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            
            // 별도 스레드에서 UI 실행 (AWT Thread)
            final String[] selectedPath = {null};
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            
            javax.swing.SwingUtilities.invokeLater(() -> {
                javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
                fc.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
                fc.setDialogTitle("프로젝트 폴더 선택");
                
                // 항상 최상단에 띄우기 위한 더미 프레임
                javax.swing.JFrame frame = new javax.swing.JFrame();
                frame.setAlwaysOnTop(true);
                frame.setDefaultCloseOperation(javax.swing.JFrame.DISPOSE_ON_CLOSE);
                
                int returnVal = fc.showOpenDialog(frame);
                if (returnVal == javax.swing.JFileChooser.APPROVE_OPTION) {
                    selectedPath[0] = fc.getSelectedFile().getAbsolutePath();
                }
                frame.dispose();
                latch.countDown();
            });
            
            latch.await(60, java.util.concurrent.TimeUnit.SECONDS);
            
            if (selectedPath[0] != null) {
                return ResponseEntity.ok(selectedPath[0]);
            } else {
                return ResponseEntity.noContent().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("탐색기를 열 수 없습니다: " + e.getMessage());
        }
    }

    private void performScan(File dir, java.util.Map<String, List<String>> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                performScan(file, result);
            } else {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".java")) {
                    if (name.contains("controller")) result.get("controller").add(file.getName());
                    else if (name.contains("service") || name.contains("svc")) result.get("service").add(file.getName());
                    else if (name.contains("dto") || name.contains("vo") || name.contains("model")) result.get("dto").add(file.getName());
                    else result.get("service").add(file.getName()); // 기본값
                } else if (name.endsWith(".xml")) {
                    result.get("mapper").add(file.getName());
                }
            }
        }
    }
}

package com.autoai;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;

public class ExcelGenerator {
    private Workbook workbook;
    private Sheet sheet;
    private int currentRow = 0;

    private CellStyle sectionStyle;
    private CellStyle subHeaderStyle;
    private CellStyle labelStyle;
    private CellStyle contentStyle;
    private CellStyle centerContentStyle; // 중앙 정렬용 추가

    public void generate(String jsonData, String outputPath) throws IOException {
        workbook = new XSSFWorkbook();
        initStyles();

        JSONArray apis = new JSONArray(jsonData);
        for (int i = 0; i < apis.length(); i++) {
            JSONObject api = apis.getJSONObject(i);
            String sheetName = api.optString("title", "API_" + (i + 1));
            sheetName = sheetName.replaceAll("[^a-zA-Z0-9가-힣ㄱ-ㅎㅏ-ㅣ\\s_]", "_");
            if (sheetName.length() > 30) sheetName = sheetName.substring(0, 30);
            
            sheet = workbook.createSheet(sheetName);
            currentRow = 0; 
            
            sheet.setColumnWidth(0, 4500);
            sheet.setColumnWidth(1, 6000);
            sheet.setColumnWidth(2, 4500);
            sheet.setColumnWidth(3, 4000);
            sheet.setColumnWidth(4, 25000);

            drawApiBlock(api, i + 1);
        }

        try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
            workbook.write(fileOut);
        }
        workbook.close();
    }

    private void drawApiBlock(JSONObject api, int index) {
        // [1] 상단 기본정보
        drawRowWithAutoHeight(new String[]{"번호", String.valueOf(index), "기능", api.optString("title")}, new int[]{0, 1, 2, 3}, new boolean[]{false, false, false, true});
        drawRowWithAutoHeight(new String[]{"Method", api.optString("httpMethod"), "URI", api.optString("apiPath")}, new int[]{0, 1, 2, 3}, new boolean[]{false, false, false, true});
        drawKeyValueRow("설명", api.optString("description"));
        currentRow++;

        // [2] Request 영역
        drawFullWidthBar("Request", sectionStyle);
        drawFullWidthBar("Header", subHeaderStyle);
        drawTableHeader(new String[]{"key", "value"}, new int[]{0, 1}, 1, 4);
        drawTableData(api.optJSONArray("reqHeaders"), new String[]{"key", "value"}, 1, 4, contentStyle);

        drawFullWidthBar("Parameters", subHeaderStyle);
        drawTableHeader(new String[]{"name", "value", "type", "필수", "설명"}, new int[]{0, 1, 2, 3, 4}, 0, 0);
        drawTableData5Col(api.optJSONArray("reqParams"));

        drawFullWidthBar("Body", subHeaderStyle);
        drawTableHeader(new String[]{"type", "value"}, new int[]{0, 1}, 1, 4);
        drawTableData(api.optJSONArray("reqBody"), new String[]{"type", "value"}, 1, 4, contentStyle);
        currentRow++;

        // [3] Response 영역
        drawFullWidthBar("Response", sectionStyle);
        drawFullWidthBar("Header", subHeaderStyle);
        drawTableHeader(new String[]{"key", "value"}, new int[]{0, 1}, 1, 4);
        drawTableData(api.optJSONArray("resHeaders"), new String[]{"key", "value"}, 1, 4, contentStyle);

        drawFullWidthBar("Body", subHeaderStyle);
        drawTableHeader(new String[]{"http status code", "description"}, new int[]{0, 1}, 1, 4);
        drawTableData(api.optJSONArray("resBody"), new String[]{"code", "desc"}, 1, 4, contentStyle);
    }

    private void drawRowWithAutoHeight(String[] vals, int[] cols, boolean[] isMerged) {
        Row row = sheet.createRow(currentRow++);
        float maxHeight = 22f;
        for (int i = 0; i < vals.length; i++) {
            Cell cell = row.createCell(cols[i]);
            cell.setCellValue(vals[i]);
            cell.setCellStyle(i % 2 == 0 ? labelStyle : centerContentStyle);
            if (isMerged[i]) mergeAndBorder(sheet, currentRow - 1, currentRow - 1, cols[i], 4);
            maxHeight = Math.max(maxHeight, calculateHeight(vals[i], isMerged[i] ? 35000 : 6000));
        }
        row.setHeightInPoints(maxHeight);
    }

    private void drawKeyValueRow(String key, String value) {
        Row row = sheet.createRow(currentRow++);
        createCell(row, 0, key, labelStyle);
        createCell(row, 1, value, contentStyle);
        mergeAndBorder(sheet, currentRow - 1, currentRow - 1, 1, 4);
        row.setHeightInPoints(calculateHeight(value, 40000));
    }

    private void drawTableData(JSONArray data, String[] keys, int mergeStart, int mergeEnd, CellStyle style) {
        if (data == null || data.isEmpty()) {
            Row row = sheet.createRow(currentRow++);
            for(int i=0; i<5; i++) createCell(row, i, "N/A", style);
            mergeAndBorder(sheet, currentRow - 1, currentRow - 1, mergeStart, mergeEnd);
            return;
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.getJSONObject(i);
            Row row = sheet.createRow(currentRow++);
            createCell(row, 0, item.optString(keys[0]), centerContentStyle);
            String val2 = item.optString(keys[1]);
            createCell(row, 1, val2, style);
            mergeAndBorder(sheet, currentRow - 1, currentRow - 1, mergeStart, mergeEnd);
            row.setHeightInPoints(calculateHeight(val2, 35000));
        }
    }

    private void drawTableData5Col(JSONArray data) {
        if (data == null || data.isEmpty()) {
            Row row = sheet.createRow(currentRow++);
            for(int i=0; i<5; i++) createCell(row, i, "N/A", contentStyle);
            return;
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.getJSONObject(i);
            Row row = sheet.createRow(currentRow++);
            createCell(row, 0, item.optString("name"), centerContentStyle);
            createCell(row, 1, item.optString("value"), contentStyle);
            createCell(row, 2, item.optString("type"), centerContentStyle);
            createCell(row, 3, item.optString("required"), centerContentStyle);
            String desc = item.optString("desc");
            createCell(row, 4, desc, contentStyle);
            row.setHeightInPoints(calculateHeight(desc, 15000));
        }
    }

    private float calculateHeight(String text, int width) {
        if (text == null || text.isEmpty()) return 22f;
        int charPerLine = width / 256; 
        String[] lines = text.split("\n");
        int totalLines = 0;
        for(String line : lines) {
            totalLines += Math.ceil((float)line.length() / charPerLine);
        }
        return Math.max(22f, totalLines * 16f + 8f);
    }

    private void drawFullWidthBar(String text, CellStyle style) {
        Row row = sheet.createRow(currentRow++);
        row.setHeightInPoints(28f);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(style);
        mergeAndBorder(sheet, currentRow - 1, currentRow - 1, 0, 4);
    }

    private void drawTableHeader(String[] names, int[] cols, int lastMergeStart, int lastMergeEnd) {
        Row row = sheet.createRow(currentRow++);
        row.setHeightInPoints(22f);
        for (int i = 0; i < names.length; i++) {
            Cell cell = row.createCell(cols[i]);
            cell.setCellValue(names[i]);
            cell.setCellStyle(labelStyle);
        }
        if (lastMergeStart > 0) mergeAndBorder(sheet, currentRow - 1, currentRow - 1, lastMergeStart, lastMergeEnd);
    }

    private void createCell(Row row, int col, String val, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(val);
        cell.setCellStyle(style);
    }

    private void mergeAndBorder(Sheet sheet, int r1, int r2, int c1, int c2) {
        CellRangeAddress region = new CellRangeAddress(r1, r2, c1, c2);
        sheet.addMergedRegion(region);
        RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
    }

    private void initStyles() {
        Font defaultFont = workbook.createFont();
        defaultFont.setFontName("맑은 고딕");
        defaultFont.setFontHeightInPoints((short) 10);

        Font boldFont = workbook.createFont();
        boldFont.setFontName("맑은 고딕");
        boldFont.setFontHeightInPoints((short) 10);
        boldFont.setBold(true);

        sectionStyle = workbook.createCellStyle();
        sectionStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        sectionStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        sectionStyle.setAlignment(HorizontalAlignment.CENTER);
        sectionStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        Font f1 = workbook.createFont(); f1.setFontName("맑은 고딕"); f1.setBold(true); f1.setColor(IndexedColors.WHITE.getIndex());
        sectionStyle.setFont(f1);
        setBorders(sectionStyle);

        subHeaderStyle = workbook.createCellStyle();
        subHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        subHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        subHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        subHeaderStyle.setFont(boldFont);
        setBorders(subHeaderStyle);

        labelStyle = workbook.createCellStyle();
        labelStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        labelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        labelStyle.setAlignment(HorizontalAlignment.CENTER);
        labelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        labelStyle.setFont(boldFont);
        setBorders(labelStyle);

        contentStyle = workbook.createCellStyle();
        contentStyle.setVerticalAlignment(VerticalAlignment.TOP);
        contentStyle.setWrapText(true);
        contentStyle.setFont(defaultFont);
        setBorders(contentStyle);

        centerContentStyle = workbook.createCellStyle();
        centerContentStyle.cloneStyleFrom(contentStyle);
        centerContentStyle.setAlignment(HorizontalAlignment.CENTER);
        centerContentStyle.setVerticalAlignment(VerticalAlignment.CENTER);
    }

    private void setBorders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
    }
}

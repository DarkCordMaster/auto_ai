package com.autoai;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExcelMerger {

    public void merge(List<MultipartFile> files, String outputPath) throws IOException {
        Workbook targetWorkbook = new XSSFWorkbook();
        // 스타일 중복 생성을 방지하기 위한 캐시
        Map<Integer, CellStyle> styleMap = new HashMap<>();

        for (MultipartFile file : files) {
            try (Workbook sourceWorkbook = new XSSFWorkbook(file.getInputStream())) {
                for (int i = 0; i < sourceWorkbook.getNumberOfSheets(); i++) {
                    Sheet sourceSheet = sourceWorkbook.getSheetAt(i);
                    String sheetName = getUniqueSheetName(targetWorkbook, sourceSheet.getSheetName());
                    Sheet targetSheet = targetWorkbook.createSheet(sheetName);
                    
                    copySheet(sourceSheet, targetSheet, targetWorkbook, styleMap);
                }
            }
        }

        try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
            targetWorkbook.write(fileOut);
        }
        targetWorkbook.close();
    }

    private String getUniqueSheetName(Workbook workbook, String name) {
        String baseName = name;
        int count = 1;
        while (workbook.getSheet(name) != null) {
            name = baseName + "_" + count++;
        }
        if (name.length() > 31) name = name.substring(0, 31);
        return name;
    }

    private void copySheet(Sheet source, Sheet target, Workbook targetWorkbook, Map<Integer, CellStyle> styleMap) {
        // 컬럼 너비 복사
        for (int i = 0; i < 10; i++) { // 최대 10개 컬럼 정도 복사
            target.setColumnWidth(i, source.getColumnWidth(i));
        }

        for (int i = 0; i <= source.getLastRowNum(); i++) {
            Row sourceRow = source.getRow(i);
            Row targetRow = target.createRow(i);
            if (sourceRow == null) continue;

            targetRow.setHeight(sourceRow.getHeight());

            for (int j = 0; j < sourceRow.getLastCellNum(); j++) {
                Cell sourceCell = sourceRow.getCell(j);
                if (sourceCell == null) continue;

                Cell targetCell = targetRow.createCell(j);
                
                // 스타일 복사
                copyCellStyle(sourceCell, targetCell, targetWorkbook, styleMap);
                // 값 복사
                copyCellValue(sourceCell, targetCell);
            }
        }
        
        // 병합된 영역 복사
        for (int i = 0; i < source.getNumMergedRegions(); i++) {
            CellRangeAddress region = source.getMergedRegion(i);
            target.addMergedRegion(region);
        }
    }

    private void copyCellStyle(Cell sourceCell, Cell targetCell, Workbook targetWorkbook, Map<Integer, CellStyle> styleMap) {
        CellStyle sourceStyle = sourceCell.getCellStyle();
        int sourceStyleHash = sourceStyle.hashCode();
        
        if (!styleMap.containsKey(sourceStyleHash)) {
            CellStyle newStyle = targetWorkbook.createCellStyle();
            newStyle.cloneStyleFrom(sourceStyle);
            styleMap.put(sourceStyleHash, newStyle);
        }
        targetCell.setCellStyle(styleMap.get(sourceStyleHash));
    }

    private void copyCellValue(Cell source, Cell target) {
        switch (source.getCellType()) {
            case STRING: target.setCellValue(source.getStringCellValue()); break;
            case NUMERIC: target.setCellValue(source.getNumericCellValue()); break;
            case BOOLEAN: target.setCellValue(source.getBooleanCellValue()); break;
            case FORMULA: target.setCellFormula(source.getCellFormula()); break;
            case BLANK: target.setBlank(); break;
            default: break;
        }
    }
}

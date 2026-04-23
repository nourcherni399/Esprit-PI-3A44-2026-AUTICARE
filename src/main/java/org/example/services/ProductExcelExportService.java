package org.example.services;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.models.Product;
import org.example.ui.product.ProductFormUi;
import org.example.ui.product.ProductFormUi.ProductCategoryChoice;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Export Excel des produits aligné sur {@code ProduitController::buildExcelSpreadsheet}
 * (projet Symfony produitsfinal).
 */
public final class ProductExcelExportService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    private static final byte[] HEADER_RGB = hex("A7C7E7");
    private static final byte[] FOOTER_RGB = hex("F5F1EB");
    private static final byte[] QTY_RED = hex("FFCCCB");
    private static final byte[] QTY_ORANGE = hex("FFA500");
    private static final byte[] QTY_GREEN = hex("90EE90");

    private ProductExcelExportService() {
    }

    public static String defaultFileName() {
        return "export_produits_" + LocalDateTime.now().format(FILE_TS) + ".xlsx";
    }

    /**
     * Écrit le classeur (même structure que l’export admin Symfony).
     */
    public static void write(Path target, List<Product> produits, Map<Integer, String> stockNames) throws IOException {
        List<Product> sorted = produits.stream()
            .sorted(Comparator.comparing((Product p) -> p.getNom() == null ? "" : p.getNom(), String.CASE_INSENSITIVE_ORDER))
            .toList();

        try (XSSFWorkbook wb = new XSSFWorkbook();
             OutputStream out = Files.newOutputStream(target)) {
            XSSFSheet sheet = wb.createSheet("Produits");
            var colorMap = new DefaultIndexedColorMap();

            XSSFCellStyle headerStyle = wb.createCellStyle();
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillForegroundColor(new XSSFColor(HEADER_RGB, colorMap));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            var headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            XSSFCellStyle qtyStyle0 = qtyFillStyle(wb, colorMap, QTY_RED);
            XSSFCellStyle qtyStyleLt5 = qtyFillStyle(wb, colorMap, QTY_ORANGE);
            XSSFCellStyle qtyStyleGe5 = qtyFillStyle(wb, colorMap, QTY_GREEN);

            var num2 = wb.createCellStyle();
            num2.setDataFormat(wb.createDataFormat().getFormat("0.00"));

            String[] headers = {
                "ID", "Nom", "Description", "Catégorie", "Prix (DT)", "Stock", "Quantité",
                "Valeur totale du stock", "Disponible"
            };
            var headRow = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                var cell = headRow.createCell(c);
                cell.setCellValue(headers[c]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Product p : sorted) {
                var row = sheet.createRow(rowIdx);
                int excelRow1 = rowIdx + 1;

                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(nullToEmpty(p.getNom()));
                row.createCell(2).setCellValue(nullToEmpty(p.getDescription()));
                row.createCell(3).setCellValue(categorieLabel(p.getCategorie()));

                var priceCell = row.createCell(4);
                priceCell.setCellValue(p.getPrix());
                priceCell.setCellStyle(num2);

                String stockNom = stockNames.get(p.getStockId());
                row.createCell(5).setCellValue(stockNom != null && !stockNom.isBlank() ? stockNom : "");

                int q = p.getStock();
                var qtyCell = row.createCell(6);
                qtyCell.setCellValue(q);
                if (q == 0) {
                    qtyCell.setCellStyle(qtyStyle0);
                } else if (q < 5) {
                    qtyCell.setCellStyle(qtyStyleLt5);
                } else {
                    qtyCell.setCellStyle(qtyStyleGe5);
                }

                var valCell = row.createCell(7);
                valCell.setCellFormula("E" + excelRow1 + "*G" + excelRow1);
                valCell.setCellStyle(num2);

                row.createCell(8).setCellValue(p.isDisponible() ? "Oui" : "Non");

                rowIdx++;
            }

            int dataLastExcelRow = rowIdx;
            int footerRowIdx = rowIdx;

            XSSFCellStyle footerBase = wb.createCellStyle();
            footerBase.setFillForegroundColor(new XSSFColor(FOOTER_RGB, colorMap));
            footerBase.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var footFont = wb.createFont();
            footFont.setBold(true);
            footerBase.setFont(footFont);

            XSSFCellStyle footerNum = wb.createCellStyle();
            footerNum.cloneStyleFrom(footerBase);
            footerNum.setDataFormat(wb.createDataFormat().getFormat("0.00"));

            var foot = sheet.createRow(footerRowIdx);
            foot.createCell(0).setCellValue("Total");
            foot.getCell(0).setCellStyle(footerBase);
            foot.createCell(1).setCellValue(sorted.size() + " produit(s) exporté(s)");
            foot.getCell(1).setCellStyle(footerBase);

            var sumCell = foot.createCell(7);
            if (sorted.isEmpty()) {
                sumCell.setCellValue(0);
            } else {
                sumCell.setCellFormula("SUM(H2:H" + (dataLastExcelRow) + ")");
            }
            sumCell.setCellStyle(footerNum);

            for (int c = 2; c <= 6; c++) {
                foot.createCell(c).setCellStyle(footerBase);
            }
            foot.createCell(8).setCellStyle(footerBase);

            for (int c = 0; c < 9; c++) {
                sheet.autoSizeColumn(c);
            }
            sheet.createFreezePane(0, 1);

            wb.write(out);
        }
    }

    private static XSSFCellStyle qtyFillStyle(XSSFWorkbook wb, DefaultIndexedColorMap colorMap, byte[] rgb) {
        XSSFCellStyle st = wb.createCellStyle();
        st.setFillForegroundColor(new XSSFColor(rgb, colorMap));
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return st;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Libellé enum comme côté Symfony ({@code Categorie::label}), sinon chaîne vide. */
    private static String categorieLabel(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) {
            return "";
        }
        String k = dbValue.trim().toLowerCase(Locale.ROOT);
        for (ProductCategoryChoice c : ProductFormUi.getProductCategories()) {
            if (c.getDbValue().equals(k)) {
                return c.getLabel();
            }
        }
        return "";
    }

    private static byte[] hex(String rgb6) {
        int r = Integer.parseInt(rgb6.substring(0, 2), 16);
        int g = Integer.parseInt(rgb6.substring(2, 4), 16);
        int b = Integer.parseInt(rgb6.substring(4, 6), 16);
        return new byte[]{(byte) r, (byte) g, (byte) b};
    }
}

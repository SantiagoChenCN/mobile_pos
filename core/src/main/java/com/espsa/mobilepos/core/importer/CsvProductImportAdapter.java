package com.espsa.mobilepos.core.importer;

import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CsvProductImportAdapter implements ProductImportAdapter {
    private static final String BARCODE = "barcode";
    private static final String NAME = "name";
    private static final String PRICE = "price";
    private static final String CATEGORY = "category";
    private static final String UNIT = "unit";

    @Override
    public ImportFormat format() {
        return ImportFormat.GENERIC_CSV;
    }

    @Override
    public ProductImportResult importProducts(InputStream inputStream, String sourceFileName)
            throws ProductImportException {
        if (inputStream == null) {
            throw new ProductImportException("CSV input is required");
        }
        List<List<String>> records = readRecords(inputStream);
        if (records.isEmpty()) {
            throw new ProductImportException("CSV file is empty");
        }
        Map<String, Integer> columns = resolveColumns(records.get(0));
        return importRows(records, columns, sourceFileName);
    }

    private ProductImportResult importRows(
            List<List<String>> records,
            Map<String, Integer> columns,
            String sourceFileName
    ) throws ProductImportException {
        List<Product> products = new ArrayList<Product>();
        List<String> warnings = new ArrayList<String>();
        Set<String> seenBarcodes = new HashSet<String>();
        for (int i = 1; i < records.size(); i++) {
            List<String> row = records.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            Product product = parseRow(row, columns, i + 1, warnings);
            if (product == null) {
                continue;
            }
            if (seenBarcodes.contains(product.barcode())) {
                warnings.add("Row " + (i + 1) + ": duplicate barcode skipped: " + product.barcode());
                continue;
            }
            seenBarcodes.add(product.barcode());
            products.add(product);
        }
        if (products.isEmpty()) {
            throw new ProductImportException("CSV has no valid products");
        }
        return new ProductImportResult(sourceFileName, Instant.now(), products, 0, warnings);
    }

    private Product parseRow(
            List<String> row,
            Map<String, Integer> columns,
            int rowNumber,
            List<String> warnings
    ) {
        String barcode = cell(row, columns.get(BARCODE));
        String name = cell(row, columns.get(NAME));
        String priceText = cell(row, columns.get(PRICE));
        if (barcode.isEmpty() || name.isEmpty() || priceText.isEmpty()) {
            warnings.add("Row " + rowNumber + ": barcode, name and price are required");
            return null;
        }
        Money price = parseMoney(priceText, rowNumber, warnings);
        if (price == null) {
            return null;
        }
        String category = cell(row, columns.get(CATEGORY));
        String unit = cell(row, columns.get(UNIT));
        return new Product(
                "csv-" + barcode,
                barcode,
                name,
                category.isEmpty() ? Product.MANUAL_ALMACEN_CATEGORY : category,
                unit.isEmpty() ? "un" : unit,
                price,
                null,
                0,
                false
        );
    }

    private Money parseMoney(String value, int rowNumber, List<String> warnings) {
        try {
            Money amount = Money.of(value.trim().replace(",", "."));
            if (amount.isZero()) {
                warnings.add("Row " + rowNumber + ": price must be greater than zero");
                return null;
            }
            return amount;
        } catch (Exception ex) {
            warnings.add("Row " + rowNumber + ": invalid price: " + value);
            return null;
        }
    }

    private Map<String, Integer> resolveColumns(List<String> header) throws ProductImportException {
        Map<String, Integer> columns = new HashMap<String, Integer>();
        for (int i = 0; i < header.size(); i++) {
            String canonical = canonicalColumn(header.get(i));
            if (!canonical.isEmpty() && !columns.containsKey(canonical)) {
                columns.put(canonical, i);
            }
        }
        requireColumn(columns, BARCODE);
        requireColumn(columns, NAME);
        requireColumn(columns, PRICE);
        return columns;
    }

    private void requireColumn(Map<String, Integer> columns, String column) throws ProductImportException {
        if (!columns.containsKey(column)) {
            throw new ProductImportException("CSV missing required column: " + column);
        }
    }

    private String canonicalColumn(String header) {
        String value = normalizeHeader(header);
        if (matches(value, "barcode", "code", "codigo", "sku", "条码", "商品条码")) {
            return BARCODE;
        }
        if (matches(value, "name", "productname", "nombre", "descripcion", "商品名", "商品名称")) {
            return NAME;
        }
        if (matches(value, "price", "saleprice", "precio", "precioventa", "售价", "价格")) {
            return PRICE;
        }
        if (matches(value, "category", "categoria", "rubro", "分类")) {
            return CATEGORY;
        }
        if (matches(value, "unit", "unidad", "uom", "单位")) {
            return UNIT;
        }
        return "";
    }

    private boolean matches(String value, String... aliases) {
        for (String alias : aliases) {
            if (value.equals(normalizeHeader(alias))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        String noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.replaceAll("[\\s_\\-]+", "");
    }

    private List<List<String>> readRecords(InputStream inputStream) throws ProductImportException {
        try {
            PushbackReader reader = new PushbackReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8), 1);
            List<List<String>> records = new ArrayList<List<String>>();
            List<String> row = new ArrayList<String>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            boolean atStart = true;
            int current;
            while ((current = reader.read()) != -1) {
                char ch = (char) current;
                if (ch == '\ufeff' && records.isEmpty() && row.isEmpty() && field.length() == 0) {
                    continue;
                }
                if (quoted) {
                    if (ch == '"') {
                        int next = reader.read();
                        if (next == '"') {
                            field.append('"');
                        } else {
                            quoted = false;
                            if (next != -1) {
                                reader.unread(next);
                            }
                        }
                    } else {
                        field.append(ch);
                    }
                    continue;
                }
                if (ch == '"' && atStart) {
                    quoted = true;
                    atStart = false;
                } else if (ch == ',') {
                    row.add(field.toString().trim());
                    field.setLength(0);
                    atStart = true;
                } else if (ch == '\n') {
                    row.add(field.toString().trim());
                    addRecord(records, row);
                    row = new ArrayList<String>();
                    field.setLength(0);
                    atStart = true;
                } else if (ch != '\r') {
                    field.append(ch);
                    if (!Character.isWhitespace(ch)) {
                        atStart = false;
                    }
                }
            }
            row.add(field.toString().trim());
            addRecord(records, row);
            return records;
        } catch (Exception ex) {
            throw new ProductImportException("Failed to read CSV", ex);
        }
    }

    private void addRecord(List<List<String>> records, List<String> row) {
        if (!isBlankRow(row)) {
            records.add(row);
        }
    }

    private boolean isBlankRow(List<String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String cell(List<String> row, Integer index) {
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value.trim();
    }
}

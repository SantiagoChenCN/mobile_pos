package com.espsa.mobilepos.core.editing;

import com.espsa.mobilepos.core.catalog.ProductRepository;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.ProductOrigin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ProductEditingService {
    private final ProductRepository productRepository;
    private final ProductPersistencePort persistencePort;
    private final ProductOptionProvider optionProvider;
    private final ProductChangeFormatter changeFormatter;

    public ProductEditingService(
            ProductRepository productRepository,
            ProductPersistencePort persistencePort,
            ProductOptionProvider optionProvider
    ) {
        this.productRepository = productRepository;
        this.persistencePort = persistencePort;
        this.optionProvider = optionProvider;
        this.changeFormatter = new ProductChangeFormatter();
    }

    public Optional<Product> findByBarcode(String barcode) {
        return productRepository.findByBarcode(barcode);
    }

    public List<Product> searchByKeyword(String query) {
        return productRepository.searchByName(query, Integer.MAX_VALUE);
    }

    public ProductCreateResult createProduct(ProductDraft draft) throws ProductPersistenceException {
        ProductValidationResult validation = validateForCreate(draft);
        if (!validation.valid()) {
            return new ProductCreateResult(null, validation, false);
        }
        Product product = buildNewProduct(validation.parsedDraft());
        productRepository.upsert(product);
        persistCurrentRepositoryProducts();
        return new ProductCreateResult(product, validation, hasSameNameProduct(product.name(), product.id()));
    }

    public ProductUpdateResult updateProduct(String productId, ProductDraft draft) throws ProductPersistenceException {
        Optional<Product> original = productRepository.findById(productId);
        if (!original.isPresent()) {
            return new ProductUpdateResult(null, null, validationError(
                    "商品不存在",
                    "El producto no existe"
            ), new ArrayList<ProductChange>(), false);
        }
        if (original.get().origin() == ProductOrigin.MS2011_SYNC) {
            return new ProductUpdateResult(original.get(), null, validationError(
                    "电脑同步商品不可在手机上编辑",
                    "Los productos sincronizados desde la computadora no se pueden editar en el telefono"
            ), new ArrayList<ProductChange>(), false);
        }
        ProductValidationResult validation = validateForUpdate(productId, draft);
        if (!validation.valid()) {
            return new ProductUpdateResult(original.get(), null, validation, new ArrayList<ProductChange>(), false);
        }
        Product updated = buildUpdatedProduct(original.get(), validation.parsedDraft());
        List<ProductChange> changes = changeFormatter.diff(original.get(), updated);
        productRepository.upsert(updated);
        persistCurrentRepositoryProducts();
        return new ProductUpdateResult(original.get(), updated, validation, changes, hasCriticalChanges(changes));
    }

    public ProductDeleteResult deleteProduct(String productId) throws ProductPersistenceException {
        Optional<Product> original = productRepository.findById(productId);
        if (original.isPresent() && original.get().origin() == ProductOrigin.MS2011_SYNC) {
            return new ProductDeleteResult(null);
        }
        Optional<Product> deleted = productRepository.deleteById(productId);
        if (!deleted.isPresent()) {
            return new ProductDeleteResult(null);
        }
        persistCurrentRepositoryProducts();
        return new ProductDeleteResult(deleted.get());
    }

    public ProductValidationResult validateForCreate(ProductDraft draft) {
        ProductValidationResult validation = validateDraft(draft);
        if (!validation.valid()) {
            return validation;
        }
        ParsedProductDraft parsed = validation.parsedDraft();
        if (productRepository.barcodeExists(parsed.barcode(), null)) {
            return validationError("条码已存在", "El codigo ya existe");
        }
        return validation;
    }

    public ProductValidationResult validateForUpdate(String productId, ProductDraft draft) {
        ProductValidationResult validation = validateDraft(draft);
        if (!validation.valid()) {
            return validation;
        }
        ParsedProductDraft parsed = validation.parsedDraft();
        if (productRepository.barcodeExists(parsed.barcode(), productId)) {
            return validationError("条码已被其他商品使用", "El codigo pertenece a otro producto");
        }
        return validation;
    }

    public boolean hasSameNameProduct(String name, String excludeProductId) {
        return productRepository.exactNameExists(name, excludeProductId);
    }

    public List<String> categoryOptions(Product currentProductOrNull) {
        return optionProvider.categoryOptions(currentProductOrNull);
    }

    public List<String> unitOptions(Product currentProductOrNull) {
        return optionProvider.unitOptions(currentProductOrNull);
    }

    public List<ProductChange> diff(Product before, Product after) {
        return changeFormatter.diff(before, after);
    }

    public String formatChangesZh(List<ProductChange> changes) {
        return changeFormatter.formatZh(changes);
    }

    public String formatChangesEs(List<ProductChange> changes) {
        return changeFormatter.formatEs(changes);
    }

    private ProductValidationResult validateDraft(ProductDraft draft) {
        List<String> zh = new ArrayList<String>();
        List<String> es = new ArrayList<String>();
        if (draft == null) {
            zh.add("商品信息不能为空");
            es.add("Los datos del producto son obligatorios");
            return new ProductValidationResult(zh, es, null);
        }
        validateBarcode(draft.barcode(), zh, es);
        validateName(draft.name(), zh, es);
        Money salePrice = parsePositiveMoney(draft.salePriceText(), "售价", "Precio", zh, es);
        Money promotionPrice = parseOptionalPositiveMoney(draft.promotionPriceText(), "促销价", "Precio promocional", zh, es);
        Long promotionQuantity = parseOptionalPositiveInteger(draft.promotionMinQuantityText(), "促销数量", "Cantidad promocional", zh, es);
        validatePromotionPair(draft, salePrice, promotionPrice, promotionQuantity, zh, es);
        if (!zh.isEmpty()) {
            return new ProductValidationResult(zh, es, null);
        }
        int parsedPromotionQuantity = promotionQuantity == null ? 0 : promotionQuantity.intValue();
        ParsedProductDraft parsed = new ParsedProductDraft(
                draft.barcode(),
                draft.name(),
                draft.category(),
                draft.unitName(),
                salePrice,
                promotionPrice,
                parsedPromotionQuantity
        );
        return ProductValidationResult.valid(parsed);
    }

    private void validateBarcode(String barcode, List<String> zh, List<String> es) {
        if (barcode == null || barcode.isEmpty()) {
            zh.add("条码不能为空");
            es.add("El codigo es obligatorio");
            return;
        }
        if (!barcode.matches("\\d{1,13}")) {
            zh.add("条码必须是 1 到 13 位数字");
            es.add("El codigo debe tener entre 1 y 13 digitos");
        }
    }

    private void validateName(String name, List<String> zh, List<String> es) {
        if (name == null || name.isEmpty()) {
            zh.add("商品名不能为空");
            es.add("El nombre es obligatorio");
        }
    }

    private Money parsePositiveMoney(
            String value,
            String fieldZh,
            String fieldEs,
            List<String> zh,
            List<String> es
    ) {
        if (value == null || value.isEmpty()) {
            zh.add(fieldZh + "不能为空");
            es.add(fieldEs + " es obligatorio");
            return null;
        }
        return parseMoney(value, fieldZh, fieldEs, zh, es);
    }

    private Money parseOptionalPositiveMoney(
            String value,
            String fieldZh,
            String fieldEs,
            List<String> zh,
            List<String> es
    ) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return parseMoney(value, fieldZh, fieldEs, zh, es);
    }

    private Money parseMoney(String value, String fieldZh, String fieldEs, List<String> zh, List<String> es) {
        try {
            Money parsed = Money.of(value.replace(',', '.'));
            if (!parsed.isZero()) {
                return parsed;
            }
            zh.add(fieldZh + "必须大于 0");
            es.add(fieldEs + " debe ser mayor que 0");
        } catch (IllegalArgumentException ex) {
            zh.add(fieldZh + "必须是有效十进制金额");
            es.add(fieldEs + " debe ser un monto decimal valido");
        }
        return null;
    }

    private Long parseOptionalPositiveInteger(
            String value,
            String fieldZh,
            String fieldEs,
            List<String> zh,
            List<String> es
    ) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (!value.matches("\\d+")) {
            zh.add(fieldZh + "必须是整数");
            es.add(fieldEs + " debe ser entero");
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                zh.add(fieldZh + "必须大于 0");
                es.add(fieldEs + " debe ser mayor que 0");
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            zh.add(fieldZh + "数字过大");
            es.add(fieldEs + " es demasiado grande");
            return null;
        }
    }

    private void validatePromotionPair(
            ProductDraft draft,
            Money salePrice,
            Money promotionPrice,
            Long promotionQuantity,
            List<String> zh,
            List<String> es
    ) {
        boolean hasPromotionPrice = draft.promotionPriceText() != null && !draft.promotionPriceText().isEmpty();
        boolean hasPromotionQuantity = draft.promotionMinQuantityText() != null && !draft.promotionMinQuantityText().isEmpty();
        if (hasPromotionPrice != hasPromotionQuantity) {
            zh.add("促销价和促销数量必须同时填写或同时留空");
            es.add("Precio y cantidad promocional deben completarse juntos o quedar vacios");
        }
        if (salePrice != null && promotionPrice != null && promotionPrice.compareTo(salePrice) >= 0) {
            zh.add("促销价必须小于售价");
            es.add("El precio promocional debe ser menor que el precio normal");
        }
    }

    private Product buildNewProduct(ParsedProductDraft draft) {
        return new Product(
                generateLocalProductId(),
                draft.barcode(),
                draft.name(),
                draft.category(),
                draft.unitName(),
                draft.salePrice(),
                draft.promotionPrice(),
                draft.promotionMinQuantity(),
                false,
                ProductOrigin.LOCAL,
                "",
                "",
                false
        );
    }

    private Product buildUpdatedProduct(Product original, ParsedProductDraft draft) {
        return new Product(
                original.id(),
                draft.barcode(),
                draft.name(),
                draft.category(),
                draft.unitName(),
                draft.salePrice(),
                draft.promotionPrice(),
                draft.promotionMinQuantity(),
                original.isManualPriceProduct(),
                original.origin(),
                original.sourceProductKey(),
                original.sourceSnapshotId(),
                original.stopped()
        );
    }

    private String generateLocalProductId() {
        return "local-" + System.currentTimeMillis();
    }

    private void persistCurrentRepositoryProducts() throws ProductPersistenceException {
        persistencePort.saveManualProducts(productRepository.all());
    }

    private ProductValidationResult validationError(String errorZh, String errorEs) {
        List<String> zh = new ArrayList<String>();
        List<String> es = new ArrayList<String>();
        zh.add(errorZh);
        es.add(errorEs);
        return new ProductValidationResult(zh, es, null);
    }

    private boolean hasCriticalChanges(List<ProductChange> changes) {
        for (ProductChange change : changes) {
            if ("条码".equals(change.fieldLabelZh())
                    || "售价".equals(change.fieldLabelZh())
                    || "促销".equals(change.fieldLabelZh())) {
                return true;
            }
        }
        return false;
    }
}

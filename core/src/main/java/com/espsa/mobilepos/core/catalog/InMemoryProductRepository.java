package com.espsa.mobilepos.core.catalog;

import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.ProductOrigin;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class InMemoryProductRepository implements ProductRepository {
    private final Map<String, Product> byId = new HashMap<String, Product>();
    private final Map<String, ProductSearchEntry> searchEntryById = new HashMap<String, ProductSearchEntry>();
    private final List<Product> products = new ArrayList<Product>();
    private final List<ProductSearchEntry> searchEntries = new ArrayList<ProductSearchEntry>();

    @Override
    public List<Product> all() {
        return Collections.unmodifiableList(new ArrayList<Product>(products));
    }

    @Override
    public Optional<Product> findById(String productId) {
        if (productId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(productId.trim()));
    }

    @Override
    public Optional<Product> findByBarcode(String barcode) {
        List<Product> matches = findAllByBarcode(barcode);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        for (Product product : matches) {
            if (product.origin() == ProductOrigin.MS2011_SYNC) {
                // Legacy checkout callers use this normal-sale lookup. A stopped authoritative
                // sync record must block a same-barcode LOCAL fallback until S10 presents it.
                return product.stopped() ? Optional.<Product>empty() : Optional.of(product);
            }
        }
        return Optional.of(matches.get(0));
    }

    @Override
    public List<Product> findAllByBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String needle = barcode.trim();
        List<Product> matches = new ArrayList<Product>();
        for (Product product : products) {
            if (needle.equals(product.barcode())) {
                matches.add(product);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    @Override
    public List<Product> searchByName(String query, int limit) {
        if (query == null || query.trim().isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        String normalizedQuery = normalizeSearchText(query);
        if (normalizedQuery.isEmpty()) {
            return Collections.emptyList();
        }
        String[] keywords = keywordsForQuery(normalizedQuery);
        List<SearchHit> hits = new ArrayList<SearchHit>();
        for (ProductSearchEntry entry : searchEntries) {
            int score = scoreEntry(entry, normalizedQuery, keywords);
            if (score >= 0) {
                hits.add(new SearchHit(entry.product(), score));
            }
        }
        Collections.sort(hits, new Comparator<SearchHit>() {
            @Override
            public int compare(SearchHit left, SearchHit right) {
                int scoreCompare = Integer.compare(right.score, left.score);
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                int lengthCompare = Integer.compare(left.product.name().length(), right.product.name().length());
                if (lengthCompare != 0) {
                    return lengthCompare;
                }
                return left.product.name().compareToIgnoreCase(right.product.name());
            }
        });

        List<Product> results = new ArrayList<Product>();
        int resultCount = Math.min(limit, hits.size());
        for (int i = 0; i < resultCount; i++) {
            results.add(hits.get(i).product);
        }
        return results;
    }

    private int scoreEntry(ProductSearchEntry entry, String normalizedQuery, String[] keywords) {
        for (String keyword : keywords) {
            if (!keyword.isEmpty() && !entry.searchable().contains(keyword)) {
                return -1;
            }
        }
        int score = 100;
        if (entry.normalizedBarcode().equals(normalizedQuery)) {
            score += 20000;
        }
        if (entry.normalizedName().equals(normalizedQuery)) {
            score += 10000;
        } else if (entry.normalizedName().startsWith(normalizedQuery)) {
            score += 9000;
        } else if (tokenStartsWith(entry.nameTokens(), normalizedQuery)) {
            score += 7500;
        } else if (entry.normalizedName().contains(normalizedQuery)) {
            score += 6500;
        }
        for (String keyword : keywords) {
            if (keyword.isEmpty()) {
                continue;
            }
            if (tokenEquals(entry.nameTokens(), keyword)) {
                score += 600;
            } else if (tokenStartsWith(entry.nameTokens(), keyword)) {
                score += 350;
            } else if (entry.normalizedName().contains(keyword)) {
                score += 200;
            } else if (entry.normalizedCategory().contains(keyword)) {
                score += 50;
            }
        }
        return score;
    }

    private String[] keywordsForQuery(String normalizedQuery) {
        String[] rawKeywords = tokenize(normalizedQuery);
        List<String> meaningfulKeywords = new ArrayList<String>();
        for (String keyword : rawKeywords) {
            if (!keyword.isEmpty() && !isSearchStopWord(keyword)) {
                meaningfulKeywords.add(keyword);
            }
        }
        if (meaningfulKeywords.isEmpty()) {
            return rawKeywords;
        }
        return meaningfulKeywords.toArray(new String[0]);
    }

    private boolean isSearchStopWord(String keyword) {
        return "de".equals(keyword)
                || "del".equals(keyword)
                || "el".equals(keyword)
                || "la".equals(keyword)
                || "los".equals(keyword)
                || "las".equals(keyword);
    }

    private boolean tokenEquals(String[] tokens, String keyword) {
        for (String token : tokens) {
            if (token.equals(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean tokenStartsWith(String[] tokens, String keyword) {
        for (String token : tokens) {
            if (token.startsWith(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        String noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.replaceAll("[^0-9a-z]+", " ").trim().replaceAll("\\s+", " ");
    }

    private String[] tokenize(String normalizedText) {
        if (normalizedText == null || normalizedText.isEmpty()) {
            return new String[0];
        }
        return normalizedText.split("\\s+");
    }

    @Override
    public void replaceAll(List<Product> newProducts) {
        clearIndexes();
        if (newProducts == null) {
            return;
        }
        for (Product product : newProducts) {
            addProduct(product);
        }
    }

    @Override
    public void upsert(Product product) {
        if (product == null) {
            return;
        }
        Optional<Product> existing = findById(product.id());
        if (existing.isPresent()) {
            removeIndexes(existing.get());
            for (int i = 0; i < products.size(); i++) {
                if (products.get(i).id().equals(product.id())) {
                    products.set(i, product);
                    addIndexes(product);
                    return;
                }
            }
        }
        addProduct(product);
    }

    @Override
    public Optional<Product> deleteById(String productId) {
        Optional<Product> existing = findById(productId);
        if (!existing.isPresent()) {
            return Optional.empty();
        }
        Product product = existing.get();
        products.remove(product);
        removeIndexes(product);
        return Optional.of(product);
    }

    @Override
    public boolean barcodeExists(String barcode, String excludedProductId) {
        Optional<Product> product = findByBarcode(barcode);
        return product.isPresent() && !sameId(product.get(), excludedProductId);
    }

    @Override
    public boolean exactNameExists(String name, String excludedProductId) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty()) {
            return false;
        }
        for (Product product : products) {
            if (product.name().equalsIgnoreCase(normalizedName) && !sameId(product, excludedProductId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int count() {
        return products.size();
    }

    private void addProduct(Product product) {
        products.add(product);
        addIndexes(product);
    }

    private void addIndexes(Product product) {
        byId.put(product.id(), product);
        if (!(product.origin() == ProductOrigin.MS2011_SYNC && product.stopped())) {
            ProductSearchEntry entry = buildSearchEntry(product);
            searchEntryById.put(product.id(), entry);
            searchEntries.add(entry);
        }
    }

    private void removeIndexes(Product product) {
        byId.remove(product.id());
        searchEntryById.remove(product.id());
        removeSearchEntry(product.id());
    }

    private void clearIndexes() {
        byId.clear();
        searchEntryById.clear();
        products.clear();
        searchEntries.clear();
    }

    private void removeSearchEntry(String productId) {
        for (int i = 0; i < searchEntries.size(); i++) {
            if (searchEntries.get(i).product().id().equals(productId)) {
                searchEntries.remove(i);
                return;
            }
        }
    }

    private ProductSearchEntry buildSearchEntry(Product product) {
        String normalizedName = normalizeSearchText(product.name());
        return new ProductSearchEntry(
                product,
                normalizedName,
                normalizeSearchText(product.barcode()),
                normalizeSearchText(product.category()),
                normalizeSearchText(product.unitName()),
                tokenize(normalizedName)
        );
    }

    private boolean sameId(Product product, String productId) {
        return productId != null && product.id().equals(productId.trim());
    }

    private static final class SearchHit {
        private final Product product;
        private final int score;

        private SearchHit(Product product, int score) {
            this.product = product;
            this.score = score;
        }
    }
}

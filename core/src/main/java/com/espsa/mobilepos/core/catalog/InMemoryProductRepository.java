package com.espsa.mobilepos.core.catalog;

import com.espsa.mobilepos.core.model.Product;

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
    private final Map<String, Product> byBarcode = new HashMap<String, Product>();
    private final List<Product> products = new ArrayList<Product>();

    @Override
    public Optional<Product> findByBarcode(String barcode) {
        if (barcode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byBarcode.get(barcode.trim()));
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
        for (Product product : products) {
            int score = scoreProduct(product, normalizedQuery, keywords);
            if (score >= 0) {
                hits.add(new SearchHit(product, score));
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

    private int scoreProduct(Product product, String normalizedQuery, String[] keywords) {
        String name = normalizeSearchText(product.name());
        String barcode = normalizeSearchText(product.barcode());
        String category = normalizeSearchText(product.category());
        String unitName = normalizeSearchText(product.unitName());
        String searchable = name + " " + barcode + " " + category + " " + unitName;
        for (String keyword : keywords) {
            if (!keyword.isEmpty() && !searchable.contains(keyword)) {
                return -1;
            }
        }
        int score = 100;
        if (barcode.equals(normalizedQuery)) {
            score += 20000;
        }
        if (name.equals(normalizedQuery)) {
            score += 10000;
        } else if (name.startsWith(normalizedQuery)) {
            score += 9000;
        } else if (tokenStartsWith(name, normalizedQuery)) {
            score += 7500;
        } else if (name.contains(normalizedQuery)) {
            score += 6500;
        }
        for (String keyword : keywords) {
            if (keyword.isEmpty()) {
                continue;
            }
            if (tokenEquals(name, keyword)) {
                score += 600;
            } else if (tokenStartsWith(name, keyword)) {
                score += 350;
            } else if (name.contains(keyword)) {
                score += 200;
            } else if (category.contains(keyword)) {
                score += 50;
            }
        }
        return score;
    }

    private String[] keywordsForQuery(String normalizedQuery) {
        String[] rawKeywords = normalizedQuery.split("\\s+");
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

    private boolean tokenEquals(String text, String keyword) {
        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            if (token.equals(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean tokenStartsWith(String text, String keyword) {
        String[] tokens = text.split("\\s+");
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

    @Override
    public void replaceAll(List<Product> newProducts) {
        byBarcode.clear();
        products.clear();
        if (newProducts == null) {
            return;
        }
        for (Product product : newProducts) {
            products.add(product);
            if (!product.barcode().isEmpty()) {
                byBarcode.put(product.barcode(), product);
            }
        }
    }

    @Override
    public int count() {
        return products.size();
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

package com.dris.v2.backend.service.local.search;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DefaultSearchSynonymRegistry implements SearchSynonymRegistry {
    private final Map<String, List<String>> synonyms;
    private final List<String> allTerms;

    public DefaultSearchSynonymRegistry() {
        Map<String, List<String>> groups = new LinkedHashMap<String, List<String>>();
        register(groups, "electric vehicle", "ev");
        register(groups, "outage", "power outage");
        register(groups, "dataset", "data set");
        register(groups, "resource");
        register(groups, "service", "api");
        register(groups, "tool");
        register(groups, "download", "downloadable");
        register(groups, "csv", "comma separated values");
        register(groups, "document", "doc", "docx", "pdf");
        this.synonyms = Collections.unmodifiableMap(groups);
        this.allTerms = Collections.unmodifiableList(new ArrayList<String>(groups.keySet()));
    }

    @Override
    public List<String> lookup(String term) {
        if (SearchTextSupport.isBlank(term)) {
            return Collections.emptyList();
        }
        List<String> values = synonyms.get(SearchTextSupport.safeLower(term));
        return values == null ? Collections.<String>emptyList() : values;
    }

    @Override
    public List<String> allTerms() {
        return allTerms;
    }

    private void register(Map<String, List<String>> target, String... values) {
        Set<String> normalized = new LinkedHashSet<String>();
        for (String value : values) {
            String trimmed = SearchTextSupport.trim(value);
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        for (String value : normalized) {
            List<String> peers = new ArrayList<String>();
            for (String peer : normalized) {
                if (!SearchTextSupport.safeLower(peer).equals(SearchTextSupport.safeLower(value))) {
                    peers.add(peer);
                }
            }
            target.put(SearchTextSupport.safeLower(value), Collections.unmodifiableList(peers));
        }
    }
}

package com.openecosystem.os.search;

import java.util.List;

public record SearchResponse(String query, String backend, List<SearchResultResponse> results) {}

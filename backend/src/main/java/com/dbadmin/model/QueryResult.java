package com.dbadmin.model;

import java.util.List;
import java.util.Map;

public class QueryResult {
    private List<Map<String, Object>> data;
    private List<String> columns;
    private Long totalCount;
    private Integer currentPage;
    private Integer pageSize;

    public QueryResult() {}

    public QueryResult(List<Map<String, Object>> data, List<String> columns, Long totalCount) {
        this.data = data;
        this.columns = columns;
        this.totalCount = totalCount;
    }

    // Getters and Setters
    public List<Map<String, Object>> getData() {
        return data;
    }

    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(Integer currentPage) {
        this.currentPage = currentPage;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
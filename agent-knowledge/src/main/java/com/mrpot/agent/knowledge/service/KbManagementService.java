package com.mrpot.agent.knowledge.service;

import com.mrpot.agent.common.kb.KbDocument;
import com.mrpot.agent.knowledge.model.PagedResponse;
import com.mrpot.agent.knowledge.repository.KbDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KbManagementService {

    private final KbDocumentRepository repository;

    /**
     * Retrieve all KB documents with pagination.
     */
    public List<KbDocument> getAllDocuments(int page, int size) {
        log.info("Getting all KB documents – page={}, size={}", page, size);
        return repository.findAll(page, size);
    }

    /**
     * Retrieve a single KB document by its ID.
     */
    public KbDocument getDocumentById(String id) {
        log.info("Getting KB document by ID: {}", id);
        try {
            Long longId = Long.parseLong(id);
            return repository.findById(longId);
        } catch (NumberFormatException e) {
            log.error("Invalid document ID format: {}", id);
            return null;
        }
    }

    /**
     * Delete a KB document by its ID.
     *
     * @return true if a row was deleted, false if the ID was not found
     */
    public boolean deleteDocument(String id) {
        log.info("Deleting KB document with ID: {}", id);
        try {
            Long longId = Long.parseLong(id);
            boolean deleted = repository.deleteById(longId);
            if (deleted) {
                log.info("Successfully deleted KB document {}", id);
            } else {
                log.warn("KB document {} not found for deletion", id);
            }
            return deleted;
        } catch (NumberFormatException e) {
            log.error("Invalid document ID format for deletion: {}", id);
            return false;
        }
    }

    /**
     * Fuzzy (ILIKE) search across content, doc_type, and metadata.
     */
    public List<KbDocument> fuzzySearch(String keyword, String docType, int page, int size) {
        log.info("Fuzzy searching KB – keyword='{}', docType='{}', page={}, size={}",
                keyword, docType, page, size);
        return repository.fuzzySearch(keyword, docType, page, size);
    }

    public PagedResponse<KbDocument> searchDocuments(String keyword, String docType, int page, int size) {
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.min(Math.max(size, 1), 100);

        if (keyword == null || keyword.isBlank()) {
            List<KbDocument> content = repository.findAll(resolvedPage, resolvedSize);
            long totalElements = repository.count();
            int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / resolvedSize);
            return new PagedResponse<>(content, resolvedPage, resolvedSize, totalElements, totalPages);
        }

        List<KbDocument> content = repository.fuzzySearch(keyword, docType, resolvedPage, resolvedSize);
        long totalElements = repository.fuzzySearchCount(keyword, docType);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / resolvedSize);
        return new PagedResponse<>(content, resolvedPage, resolvedSize, totalElements, totalPages);
    }

    /**
     * Return total document count.
     */
    public long getDocumentCount() {
        return repository.count();
    }
}

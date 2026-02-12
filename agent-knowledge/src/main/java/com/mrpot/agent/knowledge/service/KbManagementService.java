package com.mrpot.agent.knowledge.service;

import com.mrpot.agent.common.kb.KbDocument;
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

    /**
     * Return total document count.
     */
    public long getDocumentCount() {
        return repository.count();
    }
}

package com.mrpot.agent.knowledge.repository;

import com.mrpot.agent.common.kb.KbDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Sql(scripts = {"/cleanup-h2.sql", "/data-h2.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class KbDocumentRepositoryTest {

    @Autowired
    private KbDocumentRepository repository;

    @Test
    void findAll_returnsResults() {
        List<KbDocument> docs = repository.findAll(0, 10);
        assertThat(docs).isNotEmpty();
    }

    @Test
    void findAll_pagination_works() {
        List<KbDocument> page0 = repository.findAll(0, 2);
        List<KbDocument> page1 = repository.findAll(1, 2);
        assertThat(page0).hasSize(2);
        // IDs should be different between pages
        assertThat(page0.get(0).id()).isNotEqualTo(page1.get(0).id());
    }

    @Test
    void findById_existing_returnsDocument() {
        KbDocument doc = repository.findById(1L);
        assertThat(doc).isNotNull();
        assertThat(doc.id()).isEqualTo("1");
    }

    @Test
    void findById_notExists_returnsNull() {
        KbDocument doc = repository.findById(99999L);
        assertThat(doc).isNull();
    }

    @Test
    void deleteById_existing_returnsTrue() {
        // Insert a temporary row to delete
        KbDocument before = repository.findById(1L);
        assertThat(before).isNotNull();
        boolean deleted = repository.deleteById(1L);
        assertThat(deleted).isTrue();
        assertThat(repository.findById(1L)).isNull();
    }

    @Test
    void deleteById_notExists_returnsFalse() {
        boolean deleted = repository.deleteById(99999L);
        assertThat(deleted).isFalse();
    }

    @Test
    void fuzzySearch_findsMatchingContent() {
        List<KbDocument> results = repository.fuzzySearch("machine", null, 0, 10);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).content()).containsIgnoringCase("machine");
    }

    @Test
    void fuzzySearch_withDocTypeFilter() {
        List<KbDocument> results = repository.fuzzySearch("Spring", "GUIDE", 0, 10);
        assertThat(results).isNotEmpty();
        results.forEach(doc -> assertThat(doc.docType()).isEqualTo("GUIDE"));
    }

    @Test
    void fuzzySearch_noMatch_returnsEmpty() {
        List<KbDocument> results = repository.fuzzySearch("zzzznonexistent", null, 0, 10);
        assertThat(results).isEmpty();
    }

    @Test
    void count_returnsPositive() {
        long count = repository.count();
        assertThat(count).isGreaterThan(0);
    }
}

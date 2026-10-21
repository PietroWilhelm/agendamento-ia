package br.com.fiap.agendamentocomia.loader;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RulesLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RulesLoader.class);

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    @Value("classpath:doc/regras.pdf")
    private Resource rulesPdf;

    public RulesLoader(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Integer.class);
        if (count != null && count > 0) {
            log.info("Regras já carregadas no vector store ({} chunks). Pulando carga.", count);
            return;
        }

        var reader = new PagePdfDocumentReader(rulesPdf);
        List<Document> pages = reader.get();

        var splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(pages);

        vectorStore.add(chunks);
        log.info("Regras carregadas no vector store: {} páginas -> {} chunks.", pages.size(), chunks.size());
    }
}


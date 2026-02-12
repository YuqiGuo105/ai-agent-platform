-- Test seed data
INSERT INTO kb_documents (doc_type, content, metadata, embedding) VALUES
('FAQ', 'What is machine learning?', '{"source": "wiki", "topic": "ML"}', ''),
('FAQ', 'How does neural network work?', '{"source": "wiki", "topic": "NN"}', ''),
('GUIDE', 'Getting started with Spring Boot', '{"source": "docs", "topic": "Spring"}', ''),
('GUIDE', 'Docker containerization basics', '{"source": "blog", "topic": "Docker"}', ''),
('ARTICLE', 'Deep learning and natural language processing', '{"source": "paper", "topic": "NLP"}', '');

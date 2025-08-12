# ConflictRadar Data Processing Service

> **Advanced NLP analysis and document indexing for conflict detection**

## What is this?

This microservice processes news articles from the Data Ingestion Service. It extracts entities, analyzes sentiment, resolves geographic locations, and indexes everything in Elasticsearch for search and analytics.

## Architecture

```
Kafka Events → NLP Processing → Elasticsearch + Enhanced Events
     ↓              ↓                 ↓
news-ingested  Entity Extract    Document Index
high-risk      Sentiment Score   Geographic Data
batch-processed Location Resolve  Search Analytics
```

## Technologies

- **Spring Boot 3.2.1** + **Java 21** - Core framework
- **Stanford CoreNLP** - Named Entity Recognition
- **Elasticsearch 8.11** - Document storage and search
- **Apache Kafka** - Event streaming
- **Redis** - Caching and performance
- **Docker** - Containerization

## Quick Start

```bash
# Start infrastructure
docker-compose up --build

# Check health
curl http://localhost:8081/actuator/health

# View Elasticsearch
open http://localhost:9200

# View Kibana  
open http://localhost:5601
```

## What it does

1. **Consumes events** from Data Ingestion Service
2. **Extracts entities** (people, organizations, locations)
3. **Analyzes sentiment** and conflict indicators
4. **Resolves geography** using GeoNames API
5. **Indexes documents** in Elasticsearch
6. **Publishes enhanced events** for other services

## Development Status

🚧 **Early Development Phase**

- [x] Project setup with dependencies
- [x] Docker infrastructure (Elasticsearch, Kafka, Redis)
- [x] Configuration management
- [ ] Kafka consumer implementation
- [ ] NLP processing pipeline
- [ ] Elasticsearch integration
- [ ] Geographic resolution
- [ ] Comprehensive testing

## Next Services

This service will feed data to:
- **Alert Engine** - Real-time notifications
- **Dashboard Service** - Visualization and UI
- **Graph Analytics** - Network analysis

---

**Part of the ConflictRadar platform for early conflict detection and analysis.**
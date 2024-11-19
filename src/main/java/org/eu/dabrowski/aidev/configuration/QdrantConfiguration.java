package org.eu.dabrowski.aidev.configuration;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.autoconfigure.vectorstore.qdrant.QdrantConnectionDetails;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class QdrantConfiguration {
    private final QdrantConnectionDetails qdrantConnectionDetails;

    @Value("${qdrant.arvix.collection-name}")
    private String arvixCollectionName;

    @Value("${qdrant.wektory.collection-name:wektory}")
    private String wektoryCollectionName;

    @Bean
    public QdrantClient qdrantClient() {

        QdrantGrpcClient.Builder grpcClientBuilder =
                QdrantGrpcClient.newBuilder(
                        qdrantConnectionDetails.getHost(),
                        qdrantConnectionDetails.getPort(),
            false);

        return new QdrantClient(grpcClientBuilder.build());
    }

    @Qualifier("arvix")
    @Bean
    public VectorStore arvixVectorStore(EmbeddingModel embeddingModel, QdrantClient qdrantClient) {
        return new QdrantVectorStore(qdrantClient, arvixCollectionName, embeddingModel, true);
    }

    @Qualifier("wektory")
    @Bean
    public VectorStore wektoryVectorStore(EmbeddingModel embeddingModel, QdrantClient qdrantClient) {
        return new QdrantVectorStore(qdrantClient, wektoryCollectionName, embeddingModel, true);
    }
}

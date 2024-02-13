package dev.zbendhiba.demo.telegram.openapi;


import java.util.List;


import dev.langchain4j.chain.ConversationalRetrievalChain;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import dev.langchain4j.retriever.EmbeddingStoreRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import static java.time.Duration.ofSeconds;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class Routes extends RouteBuilder {
    @ConfigProperty(name="open-api-key")
    String openApiKey;

    private EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    private  EmbeddingStore<TextSegment> embeddingStore =  new InMemoryEmbeddingStore<>();

    @Override
    public void configure() throws Exception {
        // REST endpoint to add a bio
        rest("data")
                .post("/ingest/")
                .to("direct:ingest");

        // Ingest Data
        from("direct:ingest")
                .wireTap("direct:processBio")
                .transform().simple("Thanks");

        from("direct:processBio")
            // split into paragraphs and use OpenApiTokenizer
            .split(body().tokenize("\\s*\\n\\s*\\n"))
                .setHeader("paragraphNumber", simple("${exchangeProperty.CamelSplitIndex}"))
                // Process each paragraph using the OpenAiTokenizerProcessor
                .process(new OpenAiTokenizerProcessor())
                .to("direct:processTokenizedPart").end();

        // Embed paragraphs into Vector Database
        from("direct:processTokenizedPart")
                .process(exchange -> {
                    embed(exchange.getIn().getBody(List.class));
                });


        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(openApiKey)
                .modelName(GPT_3_5_TURBO)
                .temperature(0.3)
                .timeout(ofSeconds(3000))
                .build();


        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatLanguageModel(model)
                .retriever(EmbeddingStoreRetriever.from(embeddingStore, embeddingModel))
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .promptTemplate(PromptTemplate
                        .from("Answer the following question to the best of your ability: {{question}}\n\nBase your answer on the following information:\n{{information}}"))
                .build();


        from("telegram:bots?timeout=30000")
                .log("Text received in Telegram : ${body}")
                // this is just a Hello World, we suppose that we receive only text messages from user
                .filter(simple("${body} != '/start'"))
                    .process(e->{
                        IncomingMessage incomingMessage = e.getMessage().getBody(IncomingMessage.class);
                        var openapiMessage = chain.execute(incomingMessage.getText());
                        e.getMessage().setBody(openapiMessage);

                     })
                    .log("Text to send to user based on response from ChatGPT : ${body}")
                    .to("telegram:bots")
                .end();
    }

    public void embed(List<TextSegment> textSegments )  {
        List<Embedding> embeddings = embeddingModel.embedAll(textSegments).content();
        embeddingStore.addAll(embeddings, textSegments);
    }
}

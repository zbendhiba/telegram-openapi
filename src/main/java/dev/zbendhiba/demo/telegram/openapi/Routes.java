package dev.zbendhiba.demo.telegram.openapi;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.chain.ConversationalRetrievalChain;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.retriever.EmbeddingStoreRetriever;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import jdk.jfr.Name;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class Routes extends RouteBuilder {
    @ConfigProperty(name="open-api-key")
    String openApiKey;

    // In the real world, ingesting documents would often happen separately, on a CI server or similar


    private EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    private  EmbeddingStore<TextSegment> embeddingStore =  new InMemoryEmbeddingStore<>();


    @Override
    public void configure() throws Exception {



        rest("data")
                .post("/ingest")
                .to("direct:ingest");


        from("direct:ingest")
                .process(exchange -> {
                    String data = exchange.getIn().getBody(String.class);
                   String response =  embed(data);
                   exchange.getIn().setBody(response);
                });


        // data ingestion

       /* Tokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);

       // URL resource = Thread.currentThread().getContextClassLoader().getResource("my-file.txt");


        var example =  loadDocument(resource.getPath(), new TextDocumentParser());

        DocumentSplitter documentSplitter = DocumentSplitters.recursive(50, 0,
                tokenizer);

        List<TextSegment> segments = documentSplitter.split(example);

        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        embeddingStore.addAll(embeddings, segments);*/


        // this simple way of initiating the OpenAiChatModel has very limited Timeout
     //  ChatLanguageModel model = OpenAiChatModel.withApiKey(openApiKey);

        // use this way of initating the OpenAiChatModel
        // Please be aware that if you are running this on Quarkus, errors occur when running this with logs set to true
        // use camel log instead
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(openApiKey)
                .modelName(GPT_3_5_TURBO)
                .temperature(0.3)
                .timeout(ofSeconds(3000))
                //.logRequests(true)
                //.logResponses(true)
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
                   /*   IncomingMessage incomingMessage = e.getMessage().getBody(IncomingMessage.class);
                        var openapiMessage = model.generate(incomingMessage.getText());
                        // replace Body with OpenAPI response
                        e.getMessage().setBody(openapiMessage);*/




                        IncomingMessage incomingMessage = e.getMessage().getBody(IncomingMessage.class);
                        var openapiMessage = chain.execute(incomingMessage.getText());
                        e.getMessage().setBody(openapiMessage);



                     })
                    .log("Text to send to user based on response from ChatGPT : ${body}")
                    .to("telegram:bots")
                .end();
    }

    public String embed(String data)  {
        TextSegment textSegment = TextSegment.from(data);
        Embedding embedding = embeddingModel.embed(textSegment).content();
        embeddingStore.add(embedding, textSegment);
        return "Thanks";
    }
}

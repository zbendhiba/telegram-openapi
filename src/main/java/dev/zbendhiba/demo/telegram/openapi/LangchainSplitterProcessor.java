package dev.zbendhiba.demo.telegram.openapi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class LangchainSplitterProcessor implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);
        String name = exchange.getIn().getHeader("name", String.class);

        Map<String, String> map = new HashMap<>();
        map.put("name", name);

        Metadata metadata = new Metadata(map);
        Document document = new Document(body, metadata);
        DocumentSplitter splitter = DocumentSplitters.recursive(50, 0,
                new OpenAiTokenizer(GPT_3_5_TURBO));
        List<TextSegment> segments = splitter.split(document);
        exchange.getIn().setBody(segments);
    }
}

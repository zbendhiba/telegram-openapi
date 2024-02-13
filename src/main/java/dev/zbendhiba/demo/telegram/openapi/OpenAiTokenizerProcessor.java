package dev.zbendhiba.demo.telegram.openapi;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenAiTokenizerProcessor implements Processor {

    private final Tokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);
    private final int maxTokens = 50;

    @Override
    public void process(Exchange exchange) throws Exception {
        // Get the text content from the exchange
        String text = exchange.getIn().getBody(String.class);
        String paragraphNumber = exchange.getIn().getHeader("paragraphNumber",String.class);
        String name = exchange.getIn().getHeader("name",String.class);
        Map<String, String> map = new HashMap<>();
        map.put("paragraphNumber", paragraphNumber);
        map.put("name", name);
        Metadata metadata = new Metadata(map);


        // Process each part separately
        List<TextSegment> tokenizedParts = new ArrayList<>();
        int estimateTokens = tokenizer.estimateTokenCountInText(text);
        if(estimateTokens < maxTokens){
            tokenizedParts.add(new TextSegment(text, metadata));
        }else{
            tokenizedParts.addAll(splitPartIntoSubparts(text, metadata));
        }

        // Set the tokenized parts as the body of the exchange
        exchange.getIn().setBody(tokenizedParts);
    }

    private List<TextSegment> splitPartIntoSubparts(String part, Metadata metadata) {
        // Split the part into smaller subparts based on the maximum token count
        List<TextSegment> subparts = new ArrayList<>();

        String[] parts = part.split("\\s*\\R\\s*\\R\\s*"); // additional whitespaces are ignored
        for (String subPart: parts) {
            subparts.add(new TextSegment(subPart, metadata));
        }
        return subparts;
    }
}


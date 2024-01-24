package dev.zbendhiba.demo.telegram.openapi;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import jakarta.enterprise.context.ApplicationScoped;
import static java.time.Duration.ofSeconds;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class Routes extends RouteBuilder {
    @ConfigProperty(name="open-api-key")
    String openApiKey;

    @Override
    public void configure() throws Exception {

        // this simple way of initiating the OpenAiChatModel has very limited Timeout
     //  ChatLanguageModel model = OpenAiChatModel.withApiKey(openApiKey);

        // use this way of initating the OpenAiChatModel
        // Please be aware that if you are running this on Quarkus, errors occur when running this with logs set to true
        // use camel log instead
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(openApiKey)
                .modelName(GPT_3_5_TURBO)
                .temperature(0.3)
                .timeout(ofSeconds(300))
                //.logRequests(true)
                //.logResponses(true)
                .build();

        from("telegram:bots?timeout=30000")
                .log("Text received in Telegram : ${body}")
                // this is just a Hello World, we suppose that we receive only text messages from user
                .filter(simple("${body} != '/start'"))
                    .process(e->{
                        IncomingMessage incomingMessage = e.getMessage().getBody(IncomingMessage.class);
                        var openapiMessage = model.generate(incomingMessage.getText());
                        // replace Body with OpenAPI response
                        e.getMessage().setBody(openapiMessage);
                     })
                    .log("Text to send to user based on response from ChatGPT : ${body}")
                    .to("telegram:bots")
                .end();
    }
}

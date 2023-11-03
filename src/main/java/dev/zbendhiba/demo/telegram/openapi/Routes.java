package dev.zbendhiba.demo.telegram.openapi;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class Routes extends RouteBuilder {
    @ConfigProperty(name="open-api-key")
    String openApiKey;

    @Override
    public void configure() throws Exception {
        ChatLanguageModel model = OpenAiChatModel.withApiKey(openApiKey);

        from("telegram:bots")
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

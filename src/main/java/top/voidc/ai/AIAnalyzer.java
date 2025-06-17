package top.voidc.ai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

public class AIAnalyzer {
    final private OpenAIClient client;

    public AIAnalyzer() {
        this.client = OpenAIOkHttpClient.fromEnv();
    }

    public String getResponse(String str) {
        return null;
    }

    public static void main(String[] args) {
        AIAnalyzer analyzer = new AIAnalyzer();
        String response = analyzer.getResponse("Hello, AI!");
    }
}

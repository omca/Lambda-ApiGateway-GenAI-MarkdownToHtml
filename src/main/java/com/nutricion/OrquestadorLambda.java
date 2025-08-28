package com.nutricion;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import okhttp3.*;
import java.io.IOException;

public class OrquestadorLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private static final OkHttpClient httpClient = new OkHttpClient();
  private static final Gson gson = new Gson();

  @Override
  public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
    context.getLogger().log("Iniciando la orquestación...");

    APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
    response.setHeaders(java.util.Collections.singletonMap("Content-Type", "application/json"));
    response.setHeaders(java.util.Collections.singletonMap("Access-Control-Allow-Origin", "*"));

    try {
      JsonObject requestBody = JsonParser.parseString(request.getBody()).getAsJsonObject();
      String userQuery = requestBody.get("query").getAsString();

      if (userQuery == null || userQuery.trim().isEmpty()) {
        return createErrorResponse(400, "Missing 'query' in request body");
      }

      // --- Llamar a la API del Asistente AI ---
      String assistantApiUrl = System.getenv("ASSISTANT_API_URL");
      String assistantApiKey = System.getenv("ASSISTANT_API_KEY");

      context.getLogger().log("userQuery: " + userQuery);

//      String aiPayload = "{\"model\": \"gpt-4\", \"messages\": [{\"role\": \"system\", \"content\": \"You are a helpful nutrition assistant.\"}, {\"role\": \"user\", \"content\": \"" + userQuery.replace("\"", "\\\"") + "\"}]}";
      String aiPayload = "{\"model\": \"saia:assistant:AssistantNutri\", \"messages\": [{\"role\": \"user\", \"content\": \"" + userQuery.replace("\"", "\\\"") + "\"}]}";
      context.getLogger().log("aiPayload: " + aiPayload);
      context.getLogger().log("assistantApiUrl: " + assistantApiUrl);
      context.getLogger().log("assistantApiKey: " + assistantApiKey);


      RequestBody aiBody = RequestBody.create(aiPayload, MediaType.get("application/json; charset=utf-8"));
      Request aiRequest = new Request.Builder()
        .url(assistantApiUrl)
        .addHeader("Authorization", "Bearer " + assistantApiKey)
        .post(aiBody)
        .build();

      Response aiResponse = httpClient.newCall(aiRequest).execute();
      if (!aiResponse.isSuccessful()) {
        context.getLogger().log("Error en la API del asistente: " + aiResponse.message());
        return createErrorResponse(aiResponse.code(), "Error from Assistant API");
      }

      String aiResponseBody = aiResponse.body().string();
      AssistantResponse assistantData = gson.fromJson(aiResponseBody, AssistantResponse.class);
      String markdownContent = assistantData.choices.get(0).message.content;

      // --- Llamar a la API de Conversión de Markdown a HTML ---
      String markdownApiUrl = "https://api.github.com/markdown/raw";

      RequestBody markdownBody = RequestBody.create(markdownContent, MediaType.get("text/plain; charset=utf-8"));
      Request markdownRequest = new Request.Builder()
        .url(markdownApiUrl)
        .post(markdownBody)
        .build();

      Response markdownResponse = httpClient.newCall(markdownRequest).execute();
      if (!markdownResponse.isSuccessful()) {
        context.getLogger().log("Error en la API de Markdown: " + markdownResponse.message());
        return createErrorResponse(markdownResponse.code(), "Error from Markdown API");
      }

      String finalHtml = markdownResponse.body().string();

      // --- Devolver el HTML final ---
      response.setStatusCode(200);
      response.setBody(finalHtml);
      return response;

    } catch (IOException | JsonSyntaxException | NullPointerException e) {
      context.getLogger().log("Excepción: " + e.getMessage());
      return createErrorResponse(500, "Internal Server Error");
    }
  }

  // Clases auxiliares para el parseo de JSON
  static class AssistantResponse {
    @SerializedName("choices")
    public java.util.List<Choice> choices;
  }

  static class Choice {
    @SerializedName("message")
    public Message message;
  }

  static class Message {
    @SerializedName("content")
    public String content;
  }

  private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String message) {
    APIGatewayProxyResponseEvent errorResponse = new APIGatewayProxyResponseEvent();
    errorResponse.setStatusCode(statusCode);
    errorResponse.setHeaders(java.util.Collections.singletonMap("Content-Type", "application/json"));
    errorResponse.setBody("{\"error\": \"" + message + "\"}");
    return errorResponse;
  }
}
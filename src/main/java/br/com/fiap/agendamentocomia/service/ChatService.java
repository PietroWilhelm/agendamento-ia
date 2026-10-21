package br.com.fiap.agendamentocomia.service;

import br.com.fiap.agendamentocomia.tools.AppointmentTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder builder, AppointmentTool appointmentTool, VectorStore vectorStore) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(20).build();
        this.chatClient = builder
                .defaultSystem("""
                        Você é um assistente virtual especializado em agendamentos de consultas de salão de beleza.

                        Responda sempre em português do Brasil, de forma cordial e objetiva.
                        Use as regras do salão (serviços, preços, expediente, antecedência, cancelamento e remarcação)
                        fornecidas no contexto para responder dúvidas e orientar o cliente. Não invente regras nem preços.

                        Para realizar, cancelar ou remarcar serviços, use as ferramentas disponíveis. Nunca diga que
                        realizou uma ação sem ter chamado a ferramenta correspondente e visto o resultado.
                        Se faltar alguma informação (nome completo, serviço, data ou horário), pergunte ao cliente.
                        Para saber a data de hoje, use a ferramenta de data e hora atuais.
                        Se o cancelamento ou a remarcação for com menos de 6 horas de antecedência, informe a regra
                        ao cliente e peça confirmação antes de executar.
                        Após agendar, informe ao cliente os dados do agendamento e o ID.
                        """)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().topK(4).build())
                                .build())
                .defaultTools(appointmentTool)
                .build();
    }

    public String sendMessage(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
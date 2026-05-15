package ru.qsystems.telegrambot.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.qsystems.telegrambot.config.BranchConfigurationService;
import ru.qsystems.telegrambot.model.BranchConfig;
import ru.qsystems.telegrambot.model.BranchConnection;
import ru.qsystems.telegrambot.model.QueueSystem;
import ru.qsystems.telegrambot.model.ServiceInfo;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Шлюз для работы с REST API систем очереди (Orchestra и Axioma).
 *
 * <p>Инкапсулирует различия URL/контрактов и выполняет базовую авторизацию.</p>
 */
@Singleton
public class QueueGateway {
    private static final Logger LOG = LoggerFactory.getLogger(QueueGateway.class);

    private final BranchConfigurationService branchConfigurationService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public QueueGateway(
            BranchConfigurationService branchConfigurationService,
            @Client("/") HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.branchConfigurationService = branchConfigurationService;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Получает список услуг для выбранного филиала.
     *
     * @param branch конфигурация филиала из runtime-конфига.
     * @return список услуг; при ошибке возвращается пустой список.
     */
    public List<ServiceInfo> getServices(BranchConfig branch) {
        BranchConnection connection = branchConfigurationService.connectionFor(branch);
        String url = switch (connection.queueSystem()) {
            case AXIOMA -> connection.normalizedBaseUrl()
                    + "/entrypoint/branches/" + branch.branchId() + "/services";
            case ORCHESTRA -> connection.normalizedBaseUrl()
                    + "/rest/servicepoint/branches/" + branch.branchId() + "/services/";
        };

        LOG.info("GET services: {}", url);
        HttpRequest<?> request = HttpRequest.GET(URI.create(url))
                .header(HttpHeaders.AUTHORIZATION, basicAuth(connection.login(), connection.password()))
                .accept(MediaType.APPLICATION_JSON_TYPE);
        try {
            String body = httpClient.toBlocking().retrieve(request, String.class);
            List<Map<String, Object>> raw = objectMapper.readValue(body, new TypeReference<>() {});
            List<ServiceInfo> services = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                services.add(ServiceInfo.from(item));
            }
            return services;
        } catch (Exception e) {
            LOG.error("GET services failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Создаёт визит в целевой системе очереди с параметрами из Telegram-диалога.
     *
     * @param branch конфигурация филиала.
     * @param serviceIds идентификаторы выбранных услуг.
     * @param customerId идентификатор пользователя Telegram.
     * @param customerName ФИО/имя пользователя.
     * @param clientPathAnswers ответы по клиентскому пути.
     * @return JSON-ответ системы очереди или {@link Optional#empty()} при ошибке.
     */
    public Optional<Map<String, Object>> createVisit(
            BranchConfig branch,
            List<String> serviceIds,
            String customerId,
            String customerName,
            Map<String, String> clientPathAnswers
    ) {
        BranchConnection connection = branchConfigurationService.connectionFor(branch);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("TelegramCustomerId", customerId);
        parameters.put("TelegramChatId", customerId);
        parameters.put("TelegramCustomerFullName", customerName);
        if (clientPathAnswers != null && !clientPathAnswers.isEmpty()) {
            try {
                parameters.put("TelegramClientPath", objectMapper.writeValueAsString(clientPathAnswers));
            } catch (Exception e) {
                parameters.put("TelegramClientPath", clientPathAnswers.toString());
            }
        }

        String url;
        Map<String, Object> payload = new LinkedHashMap<>();
        if (connection.queueSystem() == QueueSystem.AXIOMA) {
            url = connection.normalizedBaseUrl()
                    + "/entrypoint/branches/" + branch.branchId()
                    + "/entry-points/" + branch.entryPointId()
                    + "/visits/parameters?printTicket=false";
            payload.put("serviceIds", serviceIds);
            payload.put("parameters", parameters);
        } else {
            url = connection.normalizedBaseUrl()
                    + "/rest/entrypoint/branches/" + branch.branchId()
                    + "/entryPoints/" + branch.entryPointId()
                    + "/visits/";
            payload.put("services", serviceIds);
            payload.put("parameters", parameters);
        }

        LOG.info("POST create visit: {} serviceIds={}", url, serviceIds);
        try {
            HttpRequest<Map<String, Object>> request = HttpRequest.POST(URI.create(url), payload)
                    .contentType(MediaType.APPLICATION_JSON_TYPE)
                    .accept(MediaType.APPLICATION_JSON_TYPE)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(connection.login(), connection.password()));
            String body = httpClient.toBlocking().retrieve(request, String.class);
            Map<String, Object> response = objectMapper.readValue(body, new TypeReference<>() {});
            return Optional.of(response);
        } catch (Exception e) {
            LOG.error("Create visit failed: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private static String basicAuth(String login, String password) {
        String value = (login == null ? "" : login) + ":" + (password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}

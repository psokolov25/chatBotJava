package ru.qsystems.telegrambot.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import ru.qsystems.telegrambot.util.Env;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties("bot.runtime")
public class BotRuntimeProperties {
    private String clientPathYaml = "client_path.yml";
    private String flowOrder = "ACTION_FIRST";
    private String serviceBlacklist = "Оплата услуг";
    private String visitCallTemplate = "🔔 {visitorName}, ваш талон {ticketId}! Подойдите к окну {servicePointName}.";
    private String branchVisitCallTemplatesJson = "";
    private Map<String, String> branchVisitCallTemplates = new LinkedHashMap<>();
    private boolean multiServiceEnabled = false;
    private String branchMultiServiceEnabledJson = "";
    private Map<String, Boolean> branchMultiServiceEnabled = new LinkedHashMap<>();

    public String getClientPathYaml() {
        return Env.string("CLIENT_PATH_YAML", clientPathYaml);
    }

    public void setClientPathYaml(String clientPathYaml) {
        this.clientPathYaml = clientPathYaml;
    }

    public String getFlowOrder() {
        return Env.string("ORCHESTRA_FLOW_ORDER", flowOrder);
    }

    public void setFlowOrder(String flowOrder) {
        this.flowOrder = flowOrder;
    }

    public String getServiceBlacklist() {
        return Env.string("SERVICE_BLACKLIST", serviceBlacklist);
    }

    public void setServiceBlacklist(String serviceBlacklist) {
        this.serviceBlacklist = serviceBlacklist;
    }

    public String getVisitCallTemplate() {
        return Env.string("VISIT_CALL_TEMPLATE", visitCallTemplate);
    }

    public void setVisitCallTemplate(String visitCallTemplate) {
        this.visitCallTemplate = visitCallTemplate;
    }

    public String getBranchVisitCallTemplatesJson() {
        return Env.string("ORCHESTRA_BRANCH_VISIT_CALL_TEMPLATES", branchVisitCallTemplatesJson);
    }

    public void setBranchVisitCallTemplatesJson(String branchVisitCallTemplatesJson) {
        this.branchVisitCallTemplatesJson = branchVisitCallTemplatesJson;
    }

    public Map<String, String> getBranchVisitCallTemplates() {
        return branchVisitCallTemplates;
    }

    public void setBranchVisitCallTemplates(Map<String, String> branchVisitCallTemplates) {
        this.branchVisitCallTemplates = branchVisitCallTemplates == null ? new LinkedHashMap<>() : new LinkedHashMap<>(branchVisitCallTemplates);
    }

    public boolean isMultiServiceEnabled() {
        return Env.bool("ORCHESTRA_MULTI_SERVICE_ENABLED", multiServiceEnabled);
    }

    public void setMultiServiceEnabled(boolean multiServiceEnabled) {
        this.multiServiceEnabled = multiServiceEnabled;
    }

    public String getBranchMultiServiceEnabledJson() {
        return Env.string("ORCHESTRA_BRANCH_MULTI_SERVICE_ENABLED", branchMultiServiceEnabledJson);
    }

    public void setBranchMultiServiceEnabledJson(String branchMultiServiceEnabledJson) {
        this.branchMultiServiceEnabledJson = branchMultiServiceEnabledJson;
    }

    public Map<String, Boolean> getBranchMultiServiceEnabled() {
        return branchMultiServiceEnabled;
    }

    public void setBranchMultiServiceEnabled(Map<String, Boolean> branchMultiServiceEnabled) {
        this.branchMultiServiceEnabled = branchMultiServiceEnabled == null ? new LinkedHashMap<>() : new LinkedHashMap<>(branchMultiServiceEnabled);
    }
}

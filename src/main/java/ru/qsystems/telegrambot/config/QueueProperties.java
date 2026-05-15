package ru.qsystems.telegrambot.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import ru.qsystems.telegrambot.util.Env;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ConfigurationProperties("bot.queue")
public class QueueProperties {
    private String system = "orchestra";
    private String orchestraUrl = "http://127.0.0.1:8080/";
    private String orchestraLogin = "superadmin";
    private String orchestraPassword = "";
    private String axiomaUrl = "http://127.0.0.1:8080/";
    private String axiomaLogin = "superadmin";
    private String axiomaPassword = "";
    private String branchId = "6";
    private String branchName = "Основное отделение";
    private String branchCode = "NTR";
    private String entryPointId = "2";
    private String branchesJson = "";
    private List<Map<String, Object>> branches = new ArrayList<>();

    public String getSystem() { return Env.string("QUEUE_SYSTEM", system); }
    public void setSystem(String system) { this.system = system; }
    public String getOrchestraUrl() { return Env.string("ORCHESTRA_URL", orchestraUrl); }
    public void setOrchestraUrl(String orchestraUrl) { this.orchestraUrl = orchestraUrl; }
    public String getOrchestraLogin() { return Env.string("ORCHESTRA_LOGIN", orchestraLogin); }
    public void setOrchestraLogin(String orchestraLogin) { this.orchestraLogin = orchestraLogin; }
    public String getOrchestraPassword() { return Env.string("ORCHESTRA_PASSWORD", orchestraPassword); }
    public void setOrchestraPassword(String orchestraPassword) { this.orchestraPassword = orchestraPassword; }
    public String getAxiomaUrl() { return Env.string("AXIOMA_URL", axiomaUrl); }
    public void setAxiomaUrl(String axiomaUrl) { this.axiomaUrl = axiomaUrl; }
    public String getAxiomaLogin() { return Env.string("AXIOMA_LOGIN", axiomaLogin); }
    public void setAxiomaLogin(String axiomaLogin) { this.axiomaLogin = axiomaLogin; }
    public String getAxiomaPassword() { return Env.string("AXIOMA_PASSWORD", axiomaPassword); }
    public void setAxiomaPassword(String axiomaPassword) { this.axiomaPassword = axiomaPassword; }
    public String getBranchId() { return Env.string("BRANCH_ID", branchId); }
    public void setBranchId(String branchId) { this.branchId = branchId; }
    public String getBranchName() { return Env.string("ORCHESTRA_BRANCH_NAME", branchName); }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public String getBranchCode() { return Env.string("ORCHESTRA_BRANCH_CODE", branchCode); }
    public void setBranchCode(String branchCode) { this.branchCode = branchCode; }
    public String getEntryPointId() { return Env.string("ORCHESTRA_ENTRY_POINT_ID", entryPointId); }
    public void setEntryPointId(String entryPointId) { this.entryPointId = entryPointId; }
    public String getBranchesJson() { return Env.string("ORCHESTRA_BRANCHES", branchesJson); }
    public void setBranchesJson(String branchesJson) { this.branchesJson = branchesJson; }
    public List<Map<String, Object>> getBranches() { return branches; }
    public void setBranches(List<Map<String, Object>> branches) { this.branches = branches == null ? new ArrayList<>() : new ArrayList<>(branches); }
}

package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DispatchReadiness {
    private final Long manifestId;
    private final int manifestVersion;
    private final List<Issue> issues = new ArrayList<Issue>();
    public DispatchReadiness(Long manifestId, int manifestVersion) {
        this.manifestId = manifestId;
        this.manifestVersion = manifestVersion;
    }
    public Long getManifestId() { return manifestId; }
    public int getManifestVersion() { return manifestVersion; }
    public boolean isReady() { return issues.isEmpty(); }
    public String getBasis() { return "ADVISORY_REVALIDATED_UNDER_LOCK_AT_DISPATCH"; }
    public List<Issue> getIssues() { return Collections.unmodifiableList(issues); }
    public void add(Long shipmentId, String code, String message) { issues.add(new Issue(shipmentId, code, message)); }
    public static class Issue {
        private final Long shipmentId;
        private final String code;
        private final String message;
        public Issue(Long id, String code, String message) { shipmentId = id; this.code = code; this.message = message; }
        public Long getShipmentId() { return shipmentId; }
        public String getCode() { return code; }
        public String getMessage() { return message; }
    }
}

package net.zouxin.lab.qiniuplugin;

import org.kohsuke.stapler.DataBoundConstructor;

public class QiniuEntry {
    public String profileName, source, bucket, zone, subKey/* , formatKey */;
    public boolean noUploadOnExists, noUploadOnFailure;

    public QiniuEntry() {
    }

    @DataBoundConstructor
    public QiniuEntry(String profileName, String source, String bucket, String zone, String subKey,
    /* String formatKey, */boolean noUploadOnFailure, boolean noUploadOnExists) {
        this.profileName = profileName;
        this.source = source;
        this.bucket = bucket;
        this.zone = zone;
        this.subKey = subKey;
        this.noUploadOnExists = noUploadOnExists;
        this.noUploadOnFailure = noUploadOnFailure;
        // this.formatKey = formatKey;
    }

}

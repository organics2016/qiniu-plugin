package net.zouxin.lab.qiniuplugin;

import com.google.gson.Gson;
import com.qiniu.common.QiniuException;
import com.qiniu.common.Zone;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.*;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Sample {@link Builder}.
 * <p>
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked and a new
 * {@link QiniuPublisher} is created. The created instance is persisted to the
 * project configuration XML by using XStream, so this allows you to use
 * instance fields (like {@link #name}) to remember the configuration.
 * <p>
 * <p>
 * When a build is performed, the
 * {@link #perform(AbstractBuild, Launcher, BuildListener)} method will be
 * invoked.
 *
 * @author Kohsuke Kawaguchi
 */
public class QiniuPublisher extends Recorder {

    private final List<QiniuEntry> entries = new ArrayList<QiniuEntry>();

    public QiniuPublisher() {
        super();
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher,
                           BuildListener listener) throws IOException, InterruptedException {
        // This is where you 'build' the project.
        // Since this is a dummy, we just say 'hello world' and call that a
        // build.

        // This also shows how you can consult the global configuration of the
        // builder
        FilePath ws = build.getWorkspace();
        String wsPath = ws.getRemote() + File.separator;
        PrintStream logger = listener.getLogger();
        Map<String, String> envVars = build.getEnvironment(listener);
        final boolean buildFailed = build.getResult() == Result.FAILURE;

        logger.println("开始上传到七牛...");
        for (QiniuEntry entry : this.entries) {

            if (entry.noUploadOnFailure && buildFailed) {
                logger.println("构建失败,跳过上传");
                continue;
            }

            QiniuProfile profile = this.getDescriptor().getProfileByName(
                    entry.profileName);
            if (profile == null) {
                logger.println("找不到配置项,跳过");
                continue;
            }

            //构造一个带指定Zone对象的配置类
            Configuration cfg;
            try {
                cfg = new Configuration((Zone) Zone.class.getDeclaredMethod(entry.zone, null).invoke(null, null));
            } catch (Exception e) {
                logger.println("服务器Zone配置错误,跳过");
                continue;
            }

            //...其他参数参考类注释
            UploadManager uploadManager = new UploadManager(cfg);

            Auth auth = Auth.create(profile.getAccessKey(), profile.getSecretKey());

            String upToken;
            String expanded = Util.replaceMacro(entry.source, envVars);
            FilePath[] paths = ws.list(expanded);
            for (FilePath path : paths) {
                String keyPath = path.getRemote().replace(wsPath, "");
                String key = keyPath.replace(File.separator, "/");
                if (entry.noUploadOnExists) {
                    key = null;
                } else {
                    if (entry.subKey != null && !"".equals(entry.subKey) && key.startsWith(entry.subKey)) {
                        key = key.substring(key.indexOf(entry.subKey) + entry.subKey.length());
                    }
                }

                try {
                    upToken = auth.uploadToken(entry.bucket);

                    Response response = uploadManager.put(path.getRemote(), key, upToken);

                    //解析上传成功的结果
                    DefaultPutRet putRet = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);
                    logger.println("以 " + entry.zone + " 上传 " + keyPath + " 到 " + entry.bucket + " 成功: " + putRet.key);
                } catch (QiniuException ex) {
                    build.setResult(Result.UNSTABLE);
                    Response r = ex.response;
                    logger.println(r.toString());
                    try {
                        logger.println(r.bodyString());
                    } catch (QiniuException ex2) {
                        //ignore
                    }
                }
            }
        }
        logger.println("上传到七牛完成...");
        return true;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link QiniuPublisher}. Used as a singleton. The class is
     * marked as public so that it can be accessed from views.
     * <p>
     * <p>
     * See
     * <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension
    // This indicates to Jenkins that this is an implementation of an extension
    // point.
    public static final class DescriptorImpl extends
            BuildStepDescriptor<Publisher> {
        /**
         * To persist global configuration information, simply store it in a
         * field and call save().
         * <p>
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private final CopyOnWriteList<QiniuProfile> profiles = new CopyOnWriteList<QiniuProfile>();

        public List<QiniuProfile> getProfiles() {
            return Arrays.asList(profiles.toArray(new QiniuProfile[0]));
        }

        public QiniuProfile getProfileByName(String profileName) {
            List<QiniuProfile> profiles = this.getProfiles();
            for (QiniuProfile profile : profiles) {
                if (profileName.equals(profile.getName())) {
                    return profile;
                }
            }
            return null;
        }

        /**
         * In order to load the persisted global configuration, you have to call
         * load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value This parameter receives the value that the user has typed.
         * @return Indicates the outcome of the validation. This is sent to the
         * browser.
         * <p>
         * Note that returning {@link FormValidation#error(String)} does
         * not prevent the form from being saved. It just means that a
         * message will be displayed to the user.
         */
        public FormValidation doCheckAccessKey(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Access Key 不能为空");
            return FormValidation.ok();
        }

        public FormValidation doCheckProfileName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("设置项名称不能为空");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project
            // types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "上传到七牛";
        }

        @Override
        public QiniuPublisher newInstance(StaplerRequest req,
                                          JSONObject formData) throws FormException {
            List<QiniuEntry> entries = req.bindJSONToList(QiniuEntry.class,
                    formData.get("e"));
            QiniuPublisher pub = new QiniuPublisher();
            pub.getEntries().addAll(entries);
            return pub;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData)
                throws FormException {
            profiles.replaceBy(req.bindJSONToList(QiniuProfile.class,
                    formData.get("profile")));
            save();
            return true;
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }

    public List<QiniuEntry> getEntries() {
        return entries;
    }

}

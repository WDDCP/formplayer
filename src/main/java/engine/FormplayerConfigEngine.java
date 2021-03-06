package engine;

import exceptions.ApplicationConfigException;
import exceptions.FormattedApplicationConfigException;
import installers.FormplayerInstallerFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commcare.modern.reference.ArchiveFileRoot;
import org.commcare.modern.reference.JavaHttpRoot;
import org.commcare.util.engine.CommCareConfigEngine;
import org.javarosa.core.io.BufferedInputStream;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.ResourceReferenceFactory;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.IStorageIndexedFactory;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by willpride on 11/22/16.
 */
public class FormplayerConfigEngine extends CommCareConfigEngine {

    private final Log log = LogFactory.getLog(FormplayerConfigEngine.class);

    public FormplayerConfigEngine(IStorageIndexedFactory storageFactory,
                                  FormplayerInstallerFactory formplayerInstallerFactory,
                                  ArchiveFileRoot formplayerArchiveFileRoot) {
        super(storageFactory, formplayerInstallerFactory);
        this.mArchiveRoot = formplayerArchiveFileRoot;
        ReferenceManager.instance().addReferenceFactory(formplayerArchiveFileRoot);
    }

    @Override
    protected String downloadToTemp(String resource) {
        try {
            URL url = new URL(resource);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);  //you still need to handle redirect manully.
            HttpURLConnection.setFollowRedirects(true);

            if (conn.getResponseCode() == 400) {
                handleInstallError(conn.getErrorStream());
            } else if (conn.getResponseCode() == 503) {
                throw new RuntimeException(
                        "Server is too busy. Please try again in a moment."
                );
            } else if (conn.getResponseCode() == 500) {
                throw new ApplicationConfigException(
                        "There are errors in your application. Please fix these errors in your application before using app preview."
                );
            } else if (conn.getResponseCode() == 504) {
                throw new RuntimeException(
                        "Timed out fetching the CommCare application. Please submit a ticket if you continue to see this."
                );
            } else if (conn.getResponseCode() >= 400) {
                throw new RuntimeException(
                        "Formplayer encountered an unknown error. Please submit a ticket if you continue to see this."
                );
            }
            InputStream result = conn.getInputStream();

            File file = File.createTempFile("commcare_", ".ccz");

            FileOutputStream fos = new FileOutputStream(file);
            StreamsUtil.writeFromInputToOutput(new BufferedInputStream(result), fos);
            return file.getAbsolutePath();
        } catch (IOException e) {
            log.error("Issue downloading or create stream for " + resource);
            e.printStackTrace();
            return null;
        }
    }

    private void handleInstallError(InputStream errorStream) {
        StringWriter writer = new StringWriter();
        try {
            IOUtils.copy(errorStream, writer, "utf-8");
        } catch (IOException e) {
            log.error("Unable to read error stream", e);
        }
        String errorMessage = writer.toString();
        JSONObject errorJson = new JSONObject(errorMessage);
        if (errorJson.has("error_html")) {
            throw new FormattedApplicationConfigException(errorJson.getString("error_html"));
        }
        throw new ApplicationConfigException(errorJson.getJSONArray("errors").join(" "));
    }

    @Override
    protected void setRoots() {
        ReferenceManager.instance().addReferenceFactory(new JavaHttpRoot());
        ReferenceManager.instance().addReferenceFactory(new ResourceReferenceFactory());
        ReferenceManager.instance().addReferenceFactory(new ClasspathFileRoot());
    }

    @Override
    public void initEnvironment() {
        super.initEnvironment();
        Localization.registerLanguageReference("default",
                "jr://springfile/formplayer_translatable_strings.txt");
    }
}
